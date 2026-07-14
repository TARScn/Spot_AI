# Spot AI 优惠券设计说明

> 核对日期：2026-07-14  
> 核对范围：`VoucherController`、`VoucherOrderController`、`VoucherService`、`VoucherRepository`、MQ Consumer、SQL。

## 1. 当前功能

当前优惠券模块已实现：

- 查询全部可用活动：`GET /voucher/activities`
- 查询店铺可用券：`GET /voucher/activities/of/shop?shopId=...`
- 新增普通代金券：`POST /voucher`
- 新增秒杀券：`POST /voucher/seckill`
- 领取普通代金券：`POST /voucher-order/{voucherId}`
- 秒杀券抢购：`POST /voucher-order/seckill/{voucherId}`

前端在“优惠”页面和店铺详情页展示活动；AI 助手可通过工具查询优惠券，并对普通券领取展示确认卡片。

## 2. 数据表

| 表 | 说明 |
| --- | --- |
| `tb_voucher_0`、`tb_voucher_1` | 优惠券主表，`type=0` 普通券，`type=1` 秒杀券 |
| `tb_seckill_voucher_0`、`tb_seckill_voucher_1` | 秒杀券扩展表，保存库存、时间、参与等级 |
| `tb_voucher_order_0`、`tb_voucher_order_1` | 优惠券订单表 |
| `tb_voucher_order_router_0`、`tb_voucher_order_router_1` | 订单路由表 |
| `tb_voucher_reconcile_log_0`、`tb_voucher_reconcile_log_1` | 秒杀库存扣减对账日志 |
| `tb_rollback_failure_log` | Redis 或库存回滚失败记录 |

## 3. Redis Key

| Key | 说明 |
| --- | --- |
| `seckill:stock:{voucherId}` | 秒杀库存 |
| `seckill:order:{voucherId}` | 已成功抢单的用户 Set |
| `lock:order:{userId}:{voucherId}` | 用户维度订单锁 |

## 4. 普通代金券领取

接口：

```http
POST /voucher-order/{voucherId}
Authorization: {token}
```

流程：

1. 校验登录。
2. 校验券存在、类型为普通券、已上架。
3. 检查用户是否已领取。
4. 生成订单 ID。
5. 通过 `MqEventPublisher.publishOrRun` 发送 `NormalVoucherOrderMessage`。
6. 如果 MQ 关闭或发送失败，直接同步执行 `handleNormalVoucherOrder`。
7. 使用 Redisson 锁防止同一用户重复领取。
8. 写入订单表和订单路由表。

## 5. 秒杀券抢购

接口：

```http
POST /voucher-order/seckill/{voucherId}
Authorization: {token}
```

流程：

1. 校验登录、券类型、活动时间、上架状态。
2. 确保 Redis 秒杀库存存在；不存在则从 MySQL 回填。
3. 执行 Lua 脚本，原子判断库存和一人一单。
4. 成功后生成订单 ID。
5. 如果 `spotai.voucher.mq-enabled=true`，发送 RocketMQ 消息。
6. 如果 MQ 关闭或发送失败，同步创建订单。
7. 落库失败时回滚 Redis 库存和用户标记。

## 6. Lua 抢单逻辑

```lua
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

返回含义：

| 返回值 | 含义 |
| --- | --- |
| `0` | 抢单成功 |
| `1` | 库存不足 |
| `2` | 重复下单 |

## 7. MQ 与同步 fallback

当前有两套 MQ 开关：

| 配置 | 说明 |
| --- | --- |
| `spotai.mq.enabled` | 普通业务事件总开关 |
| `spotai.voucher.mq-enabled` | 秒杀券订单专用开关 |

相关消费者：

- `NormalVoucherOrderConsumer`
- `VoucherOrderConsumer`

如果 MQ 不可用，当前代码不会直接中断核心流程，而是同步执行 fallback。这一点和早期“必须依赖 RocketMQ”的设计不同。

## 8. 库存一致性

秒杀券使用三层保护：

1. Redis Lua 原子预扣库存。
2. Redisson 用户维度锁。
3. MySQL 乐观扣减库存。

如果 MySQL 落库失败，会执行 Redis 回滚：

```text
incr seckill:stock:{voucherId}
srem seckill:order:{voucherId} {userId}
```

回滚失败会写入 `tb_rollback_failure_log`，便于后续补偿。

## 9. AI 确认领取

AI 工具 `queryCoupons` 只查询优惠券，属于低风险。

AI 工具 `claimCoupon` 会领取普通代金券，属于中风险：

1. 模型提出领取建议。
2. 后端生成确认请求并写入 `tb_ai_tool_call_log_*`。
3. 前端展示确认卡片。
4. 用户点击确认后调用 `POST /ai/tool/confirm`。
5. 后端再执行普通券领取。

秒杀券抢购当前仍由前端业务页面触发，不交给 AI 自动执行。

## 10. 当前边界

- 没有单独的支付流程；领取或秒杀成功后直接生成订单。
- 普通券库存扣减未做复杂库存模型，主要防重复领取。
- 秒杀链路支持 MQ，也支持同步 fallback。
- 生产启用 MQ 前需要单独部署 RocketMQ。
