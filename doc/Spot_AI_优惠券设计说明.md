# Spot AI 优惠券设计说明

## 1. 文档目的

本文说明 Spot AI 后续实现优惠券功能时的设计方案。当前只编写设计文档，不编写实际功能代码。

本版设计重点：

- 添加普通券和秒杀券时涉及的操作表。
- 秒杀抢单阶段先使用 Redis 完成库存余量判断和一人一单判断。
- 抢单成功后将订单任务发送到 Kafka。
- Kafka Consumer 异步消费消息并创建订单。
- 可使用 Redisson 实现分布式锁，解决集群环境下同一用户重复下单和多实例异步消费并发问题。
- MySQL 乐观锁保留为最终库存兜底，避免 Redis 和数据库不一致时发生超卖。

## 2. 当前相关表

优惠券相关表已经在 SQL 中存在，当前按后缀 `_0`、`_1` 做分表设计。

| 表 | 作用 | 关键字段 |
|---|---|---|
| `tb_voucher_0`、`tb_voucher_1` | 优惠券主表 | `id`、`shop_id`、`title`、`pay_value`、`actual_value`、`type`、`status` |
| `tb_seckill_voucher_0`、`tb_seckill_voucher_1` | 秒杀券扩展表 | `voucher_id`、`init_stock`、`stock`、`begin_time`、`end_time` |
| `tb_voucher_order_0`、`tb_voucher_order_1` | 优惠券订单表 | `id`、`user_id`、`voucher_id`、`status`、`create_time` |
| `tb_voucher_order_router_0`、`tb_voucher_order_router_1` | 订单路由表 | `order_id`、`user_id`、`voucher_id` |
| `tb_voucher_reconcile_log_0`、`tb_voucher_reconcile_log_1` | 库存变更和对账日志 | `order_id`、`voucher_id`、`before_qty`、`change_qty`、`after_qty` |
| `tb_rollback_failure_log` | Redis 或库存回滚失败日志 | `voucher_id`、`user_id`、`order_id`、`trace_id` |

`tb_voucher.type` 含义：

| 值 | 说明 |
|---|---|
| `0` | 普通券 |
| `1` | 秒杀券 |

## 3. 添加优惠券

### 3.1 添加普通券

接口规划：

```http
POST /voucher
```

是否需要登录：建议需要管理员登录。

普通券只需要写入优惠券主表：

| 操作顺序 | 表 | 操作 | 说明 |
|---|---|---|---|
| 1 | `tb_voucher_0/1` | `insert` | 写入普通券基础信息。 |

普通券请求体建议：

```json
{
  "shopId": 1,
  "title": "80元代金券",
  "subTitle": "周一至周日均可使用",
  "rules": "无规则",
  "payValue": 2000,
  "actualValue": 8000,
  "type": 0,
  "status": 1
}
```

实现思路：

1. 校验管理员权限。
2. 校验商户是否存在。
3. 校验金额字段，`actualValue` 应大于或等于 `payValue`。
4. 生成优惠券 ID。
5. 根据 `voucherId` 或 `shopId` 计算分片，写入 `tb_voucher_0/1`。
6. 返回优惠券 ID。

### 3.2 添加秒杀券

接口规划：

```http
POST /voucher/seckill
```

是否需要登录：建议需要管理员登录。

秒杀券需要同时写入优惠券主表、秒杀券扩展表，并预热 Redis 秒杀库存：

| 操作顺序 | 表或组件 | 操作 | 说明 |
|---|---|---|---|
| 1 | `tb_voucher_0/1` | `insert` | 写入优惠券基础信息，`type=1`。 |
| 2 | `tb_seckill_voucher_0/1` | `insert` | 写入秒杀库存、开始时间、结束时间。 |
| 3 | Redis | `set` | 写入秒杀库存 `seckill:stock:{voucherId}`。 |
| 4 | Redis | `del` | 清理旧的一人一单集合 `seckill:order:{voucherId}`，仅在重新配置活动时使用。 |

秒杀券请求体建议：

```json
{
  "shopId": 1,
  "title": "100元秒杀券",
  "subTitle": "限时抢购",
  "rules": "全场通用",
  "payValue": 8000,
  "actualValue": 10000,
  "type": 1,
  "status": 1,
  "stock": 100,
  "beginTime": "2026-06-10T10:00:00",
  "endTime": "2026-06-10T20:00:00",
  "allowedLevels": "1,2",
  "minLevel": 1
}
```

Redis 预热：

```text
seckill:stock:{voucherId} = stock
```

说明：

- MySQL 中的 `tb_seckill_voucher_0/1.stock` 是最终库存。
- Redis 中的 `seckill:stock:{voucherId}` 是秒杀入口的快速库存。
- 秒杀开始前必须完成 Redis 库存预热，否则抢单接口会误判库存不足。

## 4. 秒杀下单接口

接口规划：

```http
POST /voucher-order/seckill/{voucherId}
```

是否需要登录：是。

请求参数：

| 参数 | 类型 | 位置 | 说明 |
|---|---|---|---|
| `voucherId` | number | Path | 秒杀券 ID。 |

成功响应：

```json
{
  "success": true,
  "data": 1987043235650076673,
  "errorMsg": null
}
```

`data` 为订单 ID，建议使用 `RedisIdWorker.nextId("voucher_order")` 生成。

常见失败：

| 场景 | 提示 |
|---|---|
| 未登录 | `请先登录` |
| 秒杀券不存在 | `优惠券不存在` |
| 未到开始时间 | `秒杀尚未开始` |
| 已过结束时间 | `秒杀已经结束` |
| Redis 库存不足 | `库存不足` |
| Redis 判断已下单 | `不能重复下单` |
| Kafka 消息发送失败 | `系统繁忙，请稍后重试` |

## 5. 总体方案

本版采用“两段式秒杀”：

```text
请求线程：校验活动 -> Redis 原子抢单 -> 生成订单 ID -> 发送 Kafka 消息 -> 立即返回
异步线程：Kafka Consumer 消费消息 -> Redisson 用户锁 -> 数据库查重 -> MySQL 乐观锁扣库存 -> 创建订单
```

这样做的目的：

- 秒杀入口尽量少访问 MySQL，先由 Redis 承担高并发库存判断。
- 请求线程不执行完整下单事务，减少接口响应时间。
- Kafka 削峰填谷，数据库按 Consumer 消费能力平稳写入。
- Redis 判断一人一单，Redisson 锁和数据库唯一索引作为后续兜底。
- MySQL 乐观锁保留最终库存保护，防止 Redis 异常或消息重复导致超卖。

## 6. Redis 原子抢单

### 6.1 Redis Key 设计

| Key | 类型 | 说明 |
|---|---|---|
| `seckill:stock:{voucherId}` | String | 秒杀券剩余库存。 |
| `seckill:order:{voucherId}` | Set | 已成功抢到该券的用户 ID 集合。 |

示例：

```text
seckill:stock:2001 = 100
seckill:order:2001 = {1001, 1002}
```

### 6.2 Lua 脚本

抢单阶段必须把库存判断、一人一单判断、扣减库存、记录用户放在同一个 Lua 脚本中执行，保证 Redis 侧原子性。

推荐脚本：

```lua
-- KEYS[1] = seckill:stock:{voucherId}
-- KEYS[2] = seckill:order:{voucherId}
-- ARGV[1] = userId

if tonumber(redis.call('get', KEYS[1]) or '0') <= 0 then
  return 1
end

if redis.call('sismember', KEYS[2], ARGV[1]) == 1 then
  return 2
end

redis.call('decr', KEYS[1])
redis.call('sadd', KEYS[2], ARGV[1])
return 0
```

返回码：

| 返回码 | 含义 | 业务响应 |
|---|---|---|
| `0` | 抢单成功 | 生成订单任务并入队。 |
| `1` | Redis 库存不足 | 返回 `库存不足`。 |
| `2` | 用户已抢过该券 | 返回 `不能重复下单`。 |

### 6.3 请求线程流程

请求线程只做轻量操作：

1. 从 `UserHolder` 获取 `userId`。
2. 校验 `voucherId`、活动时间和券状态。
3. 执行 Lua 脚本。
4. Lua 返回 `1`，直接返回库存不足。
5. Lua 返回 `2`，直接返回不能重复下单。
6. Lua 返回 `0`，生成订单 ID。
7. 构造订单消息 `VoucherOrderMessage`。
8. 发送到 Kafka Topic `spotai.voucher-order.create`。
9. Kafka 消息发送成功，返回订单 ID。
10. Kafka 消息发送失败，需要回滚 Redis 库存和用户标记，或记录补偿日志。

请求线程不直接写订单表。

## 7. Kafka 异步下单

### 7.1 Topic 设计

第一版直接使用 Kafka 承接异步下单任务。

推荐 Topic：

```text
spotai.voucher-order.create
```

推荐死信 Topic：

```text
spotai.voucher-order.create.dlt
```

消息对象建议字段：

| 字段 | 说明 |
|---|---|
| `orderId` | Redis 全局 ID。 |
| `userId` | 下单用户 ID。 |
| `voucherId` | 秒杀券 ID。 |
| `traceId` | 链路追踪 ID，可使用订单 ID。 |
| `createTime` | 抢单时间。 |

消息体示例：

```json
{
  "orderId": 1987043235650076673,
  "userId": 1001,
  "voucherId": 2001,
  "traceId": 1987043235650076673,
  "createTime": "2026-06-10T10:00:01"
}
```

推荐使用 `orderId` 作为 Kafka message key：

- `orderId` 分布更均匀，能减少单分区热点。
- 同一个订单的重复消息会进入同一分区，便于排查和保持单订单顺序。
- 一人一单由 Redis Lua、Redisson 用户锁和数据库唯一约束兜底，不依赖 Kafka key 实现。

可选 key 策略：

| Key | 优点 | 风险 |
|---|---|---|
| `orderId` | 分布均匀，适合高并发订单创建。 | 不能保证同一用户或同一券全局有序。 |
| `userId` | 同一用户消息在同一分区内有序。 | 大用户或异常重试可能形成热点。 |
| `voucherId` | 同一秒杀券消息在同一分区内有序。 | 热门券会形成严重分区热点。 |

### 7.2 Kafka Consumer

应用启动后创建 Kafka 消费者：

```text
VoucherOrderConsumer
```

职责：

1. 订阅 `spotai.voucher-order.create`。
2. 反序列化 `VoucherOrderMessage`。
3. 调用 `handleVoucherOrder(message)` 执行异步落库。
4. 数据库事务提交成功后再提交 Kafka offset。
5. 处理失败时进入重试流程，多次失败后投递到 `spotai.voucher-order.create.dlt`。

Kafka 的作用：

- 削峰填谷，把高并发抢单流量转为可控的异步消费流量。
- 支持多实例 Consumer Group 横向扩容。
- 提供消息持久化和 offset 机制，避免服务重启直接丢失未处理任务。
- 配合重试 Topic 和死信 Topic，提高异常场景下的可恢复性。

### 7.3 重试和死信

推荐将失败分为两类：

| 失败类型 | 处理方式 |
|---|---|
| 临时失败，例如数据库短暂不可用 | Kafka 重试，或投递到延迟重试 Topic。 |
| 业务失败，例如数据库已存在订单 | 记录日志后确认消息，避免无限重试。 |
| 多次重试仍失败 | 投递到 `spotai.voucher-order.create.dlt`，由补偿任务或人工处理。 |

### 7.4 异步下单事务

Kafka Consumer 中执行数据库事务：

1. 获取 Redisson 用户维度锁。
2. 锁内查询数据库，确认用户未购买该券。
3. 使用 MySQL 乐观锁扣减数据库库存。
4. 插入 `tb_voucher_order_0/1`。
5. 插入 `tb_voucher_order_router_0/1`。
6. 插入 `tb_voucher_reconcile_log_0/1`。
7. 提交事务。
8. 释放 Redisson 锁。

为什么异步线程还需要查重：

- Redis 已经做了一人一单判断，但数据库必须有自己的最终校验。
- 可能出现 Kafka 重复消费、Consumer 处理成功但提交 offset 前宕机、Redis 和 MySQL 状态不一致。
- 数据库查重和唯一索引是最终兜底。

## 8. Redisson 分布式锁

### 8.1 为什么可以使用 Redisson

Redisson 封装了 Redis 分布式锁，提供：

- `tryLock`
- 自动续期看门狗
- 可重入锁
- 安全释放锁

相比手写 `SET NX EX + Lua unlock`，Redisson 更适合项目后续扩展和集群部署。

### 8.2 锁粒度

推荐锁 key：

```text
lock:order:{userId}:{voucherId}
```

锁粒度说明：

| 锁粒度 | 示例 | 说明 |
|---|---|---|
| 用户 + 券 | `lock:order:1001:2001` | 推荐。同一用户同一券串行，不影响其他用户抢同一券。 |
| 只按用户 | `lock:order:1001` | 过大。用户抢不同券也会互相阻塞。 |
| 只按券 | `lock:order:2001` | 过大。所有用户抢同一券都会串行，吞吐差。 |

### 8.3 使用位置

本设计中 Redis Lua 已经在抢单入口判断“一人一单”，Redisson 锁主要用于异步落库阶段兜底：

```text
handleVoucherOrder(message)
-> RLock lock = redissonClient.getLock("lock:order:" + userId + ":" + voucherId)
-> tryLock()
-> 锁内查重、扣库存、建订单
-> unlock()
```

为什么不只依赖 Redisson：

- 如果请求入口只用 Redisson 锁再查 MySQL，会把高并发压力打到数据库。
- Lua 脚本可以在 Redis 侧快速失败，性能更适合秒杀入口。

为什么不只依赖 Lua：

- Lua 只能保证 Redis 侧原子性。
- 订单最终落在 MySQL。
- Kafka 消息可能重复消费或失败重试，所以数据库落库阶段还需要 Redisson 锁和数据库兜底。

## 9. MySQL 乐观锁作为最终库存保护

### 9.1 为什么还需要 MySQL 乐观锁

Redis 已经预扣库存，但 MySQL 仍然是最终事实来源。以下场景可能导致 Redis 和 MySQL 不一致：

- 抢单成功后 Kafka 消息发送失败。
- Kafka 消息消费失败。
- Consumer 处理成功但提交 offset 前宕机，导致消息重复消费。
- 手动补偿或重试产生重复任务。
- Redis 数据被误删或过期策略配置错误。

因此数据库扣库存仍然要使用乐观锁：

```sql
update tb_seckill_voucher_0
set stock = stock - 1
where voucher_id = ?
  and stock > 0;
```

判断更新影响行数：

| affected rows | 含义 |
|---|---|
| `1` | 数据库扣减成功，可以创建订单。 |
| `0` | 数据库库存不足或记录不存在，需要回滚 Redis 抢单状态或记录补偿。 |

### 9.2 事务边界

建议将以下操作放在同一个数据库事务中：

1. 乐观锁扣减 `tb_seckill_voucher_0/1.stock`。
2. 插入 `tb_voucher_order_0/1`。
3. 插入 `tb_voucher_order_router_0/1`。
4. 插入 `tb_voucher_reconcile_log_0/1`。

事务成功时：

```text
库存减少、订单创建、路由记录、对账日志同时成功
```

事务失败时：

```text
整体回滚，不产生库存和订单不一致
```

## 10. Redis 回滚和补偿

### 10.1 何时需要回滚 Redis

Redis 抢单成功后，以下情况需要回滚：

| 场景 | 回滚动作 |
|---|---|
| Kafka 消息发送失败 | `incr seckill:stock:{voucherId}`，`srem seckill:order:{voucherId} userId` |
| 异步落库时发现用户已下单 | `incr` 库存，`srem` 用户标记 |
| MySQL 乐观锁扣库存失败 | `incr` 库存，`srem` 用户标记 |
| 数据库事务异常 | `incr` 库存，`srem` 用户标记 |

回滚也建议使用 Lua 脚本，保证库存恢复和用户标记删除原子执行：

```lua
-- KEYS[1] = seckill:stock:{voucherId}
-- KEYS[2] = seckill:order:{voucherId}
-- ARGV[1] = userId

redis.call('incr', KEYS[1])
redis.call('srem', KEYS[2], ARGV[1])
return 0
```

### 10.2 回滚失败处理

如果 Redis 回滚失败，需要写入：

```text
tb_rollback_failure_log
```

建议记录：

| 字段 | 说明 |
|---|---|
| `voucher_id` | 秒杀券 ID。 |
| `user_id` | 用户 ID。 |
| `order_id` | 订单 ID。 |
| `trace_id` | 链路追踪 ID。 |
| `detail` | 失败详情。 |
| `source` | 来源组件，例如 `VoucherOrderConsumer`。 |
| `retry_attempts` | 已重试次数。 |

后续可以通过定时任务扫描失败日志并进行补偿。

## 11. 一人一单数据库兜底

当前 `tb_voucher_order_0/1` 没有唯一索引。为进一步保证一人一单，建议后续在订单分表上增加唯一索引：

```sql
alter table tb_voucher_order_0
add unique key uk_user_voucher (user_id, voucher_id);

alter table tb_voucher_order_1
add unique key uk_user_voucher (user_id, voucher_id);
```

说明：

- Redis Set 是入口层一人一单判断。
- Redisson 锁是异步落库阶段的并发保护。
- 唯一索引是数据库层最终兜底。

如果允许用户取消后重新购买，可以把唯一索引设计得更细，例如增加状态维度或使用独立购买资格表；第一版建议先按“不允许重复购买同一秒杀券”处理。

## 12. 分片与 ID 设计

### 12.1 订单 ID

订单 ID 建议使用当前已有的 Redis 全局 ID 生成器：

```java
redisIdWorker.nextId("voucher_order")
```

原因：

- 秒杀订单创建并发高。
- 数据库自增 ID 容易形成写入热点。
- Redis ID 支持全局唯一和趋势递增。

### 12.2 分表路由

当前数据库有：

```text
tb_voucher_order_0
tb_voucher_order_1
tb_voucher_order_router_0
tb_voucher_order_router_1
```

建议路由规则：

| 数据 | 路由字段 | 说明 |
|---|---|---|
| 订单表 | `userId` | 查询用户订单更方便。 |
| 订单路由表 | `orderId` 或 `userId` | 用于根据订单 ID 找到用户和券。 |
| 秒杀券库存表 | `voucherId` | 秒杀库存按券定位。 |

第一版如果暂不实现完整分库分表中间件，可以先封装 Repository 方法，根据 `Math.abs(id.hashCode()) % 2` 选择 `_0` 或 `_1` 表，保持和用户表分片工具的风格一致。

## 13. 推荐伪代码

### 13.1 添加秒杀券

```java
public Result<Long> addSeckillVoucher(SeckillVoucherDTO dto) {
    checkAdmin();
    validateVoucher(dto);

    Long voucherId = idWorker.nextId("voucher");

    transaction(() -> {
        insertVoucher(voucherId, dto, type = 1);
        insertSeckillVoucher(voucherId, dto.stock, dto.beginTime, dto.endTime);
    });

    redisTemplate.opsForValue().set("seckill:stock:" + voucherId, dto.stock);
    redisTemplate.delete("seckill:order:" + voucherId);

    return Result.ok(voucherId);
}
```

### 13.2 秒杀抢单

```java
public Result<Long> seckillVoucher(Long voucherId) {
    Long userId = UserHolder.getUser().getId();

    checkVoucherBaseInfo(voucherId);

    Long result = stringRedisTemplate.execute(
            SECKILL_SCRIPT,
            List.of("seckill:stock:" + voucherId, "seckill:order:" + voucherId),
            userId.toString()
    );

    if (result == 1) {
        return Result.fail("库存不足");
    }
    if (result == 2) {
        return Result.fail("不能重复下单");
    }

    Long orderId = redisIdWorker.nextId("voucher_order");
    VoucherOrderMessage message = new VoucherOrderMessage(
            orderId,
            userId,
            voucherId,
            orderId,
            LocalDateTime.now()
    );

    try {
        kafkaTemplate.send(
                "spotai.voucher-order.create",
                orderId.toString(),
                message
        ).get();
    } catch (Exception e) {
        rollbackRedisSeckill(voucherId, userId);
        return Result.fail("系统繁忙，请稍后重试");
    }

    return Result.ok(orderId);
}
```

### 13.3 Kafka 消费者

```java
@KafkaListener(
        topics = "spotai.voucher-order.create",
        groupId = "spotai-voucher-order"
)
public void onMessage(VoucherOrderMessage message) {
    try {
        handleVoucherOrder(message);
    } catch (Exception e) {
        // 交给 Kafka 重试或死信策略处理。
        throw e;
    }
}
```

### 13.4 异步落库

```java
private void handleVoucherOrder(VoucherOrderMessage message) {
    RLock lock = redissonClient.getLock(
            "lock:order:" + message.userId() + ":" + message.voucherId()
    );

    boolean locked = lock.tryLock();
    if (!locked) {
        rollbackRedisSeckill(message.voucherId(), message.userId());
        return;
    }

    try {
        transaction(() -> {
            if (existsOrder(message.userId(), message.voucherId())) {
                rollbackRedisSeckill(message.voucherId(), message.userId());
                return;
            }

            int updated = deductStockWithOptimisticLock(message.voucherId());
            if (updated == 0) {
                rollbackRedisSeckill(message.voucherId(), message.userId());
                return;
            }

            insertVoucherOrder(message.orderId(), message.userId(), message.voucherId());
            insertVoucherOrderRouter(message.orderId(), message.userId(), message.voucherId());
            insertReconcileLog(message.orderId(), message.userId(), message.voucherId(), -1);
        });
    } finally {
        lock.unlock();
    }
}
```

## 14. 后续演进方向

第一版直接使用 Kafka，目标是完成 Redis 抢单和可靠异步下单闭环。

后续生产化建议：

1. 配置 Kafka 多分区和消费者组，提高异步下单吞吐。
2. 引入 Kafka 重试 Topic 和死信 Topic。
3. 使用 `tb_voucher_reconcile_log_0/1` 做库存对账。
4. 使用 `tb_rollback_failure_log` 记录回滚失败和补偿任务。
5. 为 `tb_voucher_order_0/1` 增加唯一索引 `(user_id, voucher_id)`。
6. 使用定时任务对比 Redis 库存、MySQL 库存和订单数量。

推荐演进路径：

```text
Redis Lua 抢单 + Kafka 异步下单
-> Redisson 锁 + MySQL 乐观锁兜底
-> Kafka 重试 Topic 和死信 Topic
-> 对账和补偿
```

## 15. 设计结论

优惠券模块第一版建议采用以下策略：

| 问题 | 方案 |
|---|---|
| 普通券添加 | 只写 `tb_voucher_0/1`，`type=0`。 |
| 秒杀券添加 | 写 `tb_voucher_0/1`、`tb_seckill_voucher_0/1`，并预热 `seckill:stock:{voucherId}`。 |
| 抢单入口 | Redis Lua 原子判断库存和一人一单。 |
| 异步下单 | 抢单成功后发送 Kafka 消息，由 Kafka Consumer 消费。 |
| 订单 ID | 使用 `RedisIdWorker.nextId("voucher_order")`。 |
| 一人一单 | Redis Set 做入口判断，Redisson 用户券维度锁做异步落库保护。 |
| 库存超卖 | Redis 预扣库存，MySQL 乐观锁作为最终兜底。 |
| 重复下单兜底 | 建议增加数据库唯一索引 `(user_id, voucher_id)`。 |
| 一致性保障 | Redis 回滚、数据库事务、对账日志和失败补偿表共同保证最终一致。 |
