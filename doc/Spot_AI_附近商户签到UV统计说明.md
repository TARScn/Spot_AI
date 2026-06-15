# Spot AI 附近商户、用户签到与 UV 统计实现说明

## 1. 文档目的

本文说明 Spot AI 后续实现以下能力时的后端设计方案：

- 附近商户查询。
- 用户每日签到与连续签到统计。
- UV 独立访客统计。

这三类能力都适合使用 Redis 的专用数据结构：

| 能力 | Redis 结构 | 作用 |
|---|---|---|
| 附近商户 | GEO | 按经纬度查询附近商户并返回距离。 |
| 用户签到 | BitMap | 用极低成本记录用户某月每天是否签到。 |
| UV 统计 | HyperLogLog | 低内存估算独立访客数量。 |

## 2. 相关数据基础

### 2.1 商户表

当前 `tb_shop` 已包含附近查询所需字段：

| 字段 | 说明 |
|---|---|
| `id` | 商户 ID。 |
| `type_id` | 商户分类 ID。 |
| `name` | 商户名称。 |
| `x` | 经度。 |
| `y` | 纬度。 |
| `area` | 商圈。 |
| `address` | 地址。 |
| `avg_price` | 人均价格。 |
| `sold` | 销量。 |
| `comments` | 评论数。 |
| `score` | 评分，乘 10 保存。 |

附近商户第一版不新增 MySQL 表，直接把 `tb_shop.x/y` 预热到 Redis GEO。

### 2.2 签到表

当前 SQL 中已有 `tb_sign`：

| 字段 | 说明 |
|---|---|
| `id` | 主键。 |
| `user_id` | 用户 ID。 |
| `year` | 签到年份。 |
| `month` | 签到月份。 |
| `date` | 签到日期。 |
| `is_backup` | 是否补签。 |

第一版建议以 Redis BitMap 作为实时签到数据源，`tb_sign` 可作为后续持久化、补签、审计和运营统计表。

## 3. 附近商户功能

### 3.1 业务目标

用户进入某个商户分类页时，如果传入当前位置经纬度，系统返回附近商户列表，并按距离从近到远排序。

典型场景：

```http
GET /shop/of/type?typeId=1&current=1&x=120.149192&y=30.316078
```

如果不传 `x/y`，则退化为普通分类分页查询。

### 3.2 Redis Key 设计

```text
shop:geo:{typeId}
```

示例：

```text
shop:geo:1
shop:geo:2
```

设计原因：

- Redis GEO 本质上是 Sorted Set。
- 按商户类型拆 key，可以避免不同分类混在一起后再过滤。
- 查询“美食附近商户”时只查 `shop:geo:1`，结果更小、更快。

GEO member：

```text
shopId
```

GEO position：

```text
longitude = tb_shop.x
latitude  = tb_shop.y
```

### 3.3 数据预热流程

应用启动或管理后台触发预热：

1. 查询 `tb_shop` 全量商户。
2. 按 `type_id` 分组。
3. 写入 Redis GEO：

```text
geoadd shop:geo:{typeId} {x} {y} {shopId}
```

伪代码：

```java
List<Shop> shops = shopRepository.findAll();
Map<Long, List<Shop>> groupByType = shops.stream()
        .collect(Collectors.groupingBy(Shop::getTypeId));

for (Map.Entry<Long, List<Shop>> entry : groupByType.entrySet()) {
    String key = "shop:geo:" + entry.getKey();
    for (Shop shop : entry.getValue()) {
        redisTemplate.opsForGeo().add(
                key,
                new Point(shop.getX(), shop.getY()),
                shop.getId().toString()
        );
    }
}
```

### 3.4 附近商户查询流程

请求：

```http
GET /shop/of/type?typeId=1&current=1&x=120.149192&y=30.316078
```

流程：

1. 校验 `typeId` 和分页参数。
2. 如果未传 `x/y`，按 MySQL 分类分页查询：

```sql
select *
from tb_shop
where type_id = ?
order by sort 或 id
limit ?, ?;
```

3. 如果传入 `x/y`，使用 Redis GEO 查询：

```text
GEORADIUS 或 GEOSEARCH shop:geo:{typeId}
中心点：x, y
半径：例如 5000 米
排序：ASC
数量：current * pageSize
WITHDIST
```

4. 根据分页截取本页商户 ID。
5. 根据商户 ID 查询 MySQL 详情。
6. 按 Redis 返回的距离顺序重排。
7. 给每个商户补充 `distance` 字段。

### 3.5 返回结构建议

商户列表中建议额外返回距离：

```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "name": "103茶餐厅",
      "typeId": 1,
      "x": 120.149192,
      "y": 30.316078,
      "distance": 128.6
    }
  ],
  "errorMsg": null
}
```

`distance` 单位建议使用米。

### 3.6 更新策略

商户新增或修改坐标时：

1. 先更新 MySQL `tb_shop`。
2. 删除或更新 Redis GEO 中旧坐标。
3. 写入新坐标到 `shop:geo:{typeId}`。

如果商户类型变化：

1. 从旧 `shop:geo:{oldTypeId}` 删除该商户。
2. 写入新 `shop:geo:{newTypeId}`。

## 4. 用户签到功能

### 4.1 业务目标

用户每天可以签到一次。系统需要支持：

- 今日签到。
- 查询当月连续签到天数。
- 后续扩展签到日历、补签、积分奖励。

### 4.2 Redis Key 设计

```text
sign:{userId}:{yyyyMM}
```

示例：

```text
sign:1001:202606
```

BitMap 位含义：

| bit offset | 日期 |
|---|---|
| `0` | 1 号 |
| `1` | 2 号 |
| `2` | 3 号 |
| `dayOfMonth - 1` | 当月当天 |

### 4.3 签到接口

```http
POST /user/sign
Authorization: Bearer {token}
```

处理流程：

1. 从 `UserHolder` 获取当前用户。
2. 获取当前日期，例如 `2026-06-11`。
3. 构造 key：`sign:{userId}:202606`。
4. 计算 offset：`dayOfMonth - 1`。
5. 执行：

```text
SETBIT sign:{userId}:{yyyyMM} {offset} 1
```

6. 可选：如果 `SETBIT` 返回旧值为 `1`，说明用户今天已经签到。
7. 第一版可以重复签到返回成功，也可以返回“今日已签到”，建议返回“今日已签到”更清晰。

响应：

```json
{
  "success": true,
  "data": null,
  "errorMsg": null
}
```

### 4.4 连续签到统计接口

```http
GET /user/sign/count
Authorization: Bearer {token}
```

统计目标：

从今天开始向前数，连续多少天都已签到。

例如：

```text
6月1日 ~ 6月11日签到状态：
11101101111
今天是 11 日
从 11 日向前数：11、10、9、8 都签到，7 未签到
连续签到 = 4
```

Redis 查询：

```text
BITFIELD sign:{userId}:{yyyyMM} GET u{dayOfMonth} 0
```

含义：

- 从 offset 0 开始，读取本月 1 号到今天的所有签到位。
- 得到一个整数后，从低位向高位统计连续的 `1`。

伪代码：

```java
long num = bitfieldResult;
int count = 0;
while ((num & 1) == 1) {
    count++;
    num >>>= 1;
}
```

注意：

- Redis 返回的位序需要和实现中 `BITFIELD` 的读取方式对应，测试时要重点验证。
- 如果当天未签到，连续签到数为 `0`。

### 4.5 MySQL 持久化策略

第一版可以只写 Redis BitMap，因为签到主要用于高频查询和连续天数计算。

后续如果要做补签、积分、运营报表，建议异步持久化到 `tb_sign`：

```text
POST /user/sign
  -> SETBIT
  -> 发送 RocketMQ 签到事件 user.sign
  -> Consumer 写 tb_sign
```

或者每天凌晨批量扫描昨日 BitMap，落库到 `tb_sign`。

建议给 `tb_sign` 增加唯一索引：

```sql
alter table tb_sign add unique key uk_user_date (user_id, date);
```

避免重复签到记录。

## 5. UV 统计功能

### 5.1 业务目标

UV 表示独立访客数量。同一个用户或同一个匿名访客在同一天访问多次，只计为 1 个 UV。

常见统计维度：

- 全站日 UV。
- 商户详情页日 UV。
- 探店笔记日 UV。
- 活动页日 UV。

### 5.2 Redis HyperLogLog Key 设计

全站日 UV：

```text
uv:site:{yyyyMMdd}
```

商户详情日 UV：

```text
uv:shop:{shopId}:{yyyyMMdd}
```

探店笔记日 UV：

```text
uv:blog:{blogId}:{yyyyMMdd}
```

活动页日 UV：

```text
uv:page:{pageCode}:{yyyyMMdd}
```

### 5.3 访客标识设计

优先级：

1. 已登录用户：使用 `user:{userId}`。
2. 未登录用户：使用前端生成的匿名访客 ID，例如 Cookie 或 localStorage 中的 `visitorId`。
3. 如果都没有：可以退化使用 `ip + userAgent` 哈希，但准确性较差。

示例 member：

```text
user:1001
visitor:2f1d0a8e4c9b
```

### 5.4 写入 UV

页面访问时执行：

```text
PFADD uv:site:20260611 user:1001
PFADD uv:shop:1:20260611 user:1001
```

Java 伪代码：

```java
stringRedisTemplate.opsForHyperLogLog()
        .add("uv:site:" + today, visitor);
```

### 5.5 查询 UV

```text
PFCOUNT uv:site:20260611
PFCOUNT uv:shop:1:20260611
```

接口建议：

```http
GET /stats/uv/site?date=2026-06-11
GET /stats/uv/shop/{shopId}?date=2026-06-11
```

响应：

```json
{
  "success": true,
  "data": 1024,
  "errorMsg": null
}
```

### 5.6 HyperLogLog 取舍

优点：

- 内存占用极低。
- 适合大规模 UV 估算。
- 写入和统计都很快。

缺点：

- 是估算值，不适合做强精确计费。
- 不能反查具体访问用户。

因此：

- UV、DAU、页面访问独立人数适合用 HyperLogLog。
- 需要精确明细、审计或风控时，应另外写 MySQL 日志表或行为日志表。

## 6. API 设计汇总

### 6.1 附近商户

```http
GET /shop/of/type?typeId=1&current=1&x=120.149192&y=30.316078
```

| 参数 | 必填 | 说明 |
|---|---|---|
| `typeId` | 是 | 商户分类 ID。 |
| `current` | 否 | 页码，默认 1。 |
| `x` | 否 | 用户当前经度。 |
| `y` | 否 | 用户当前纬度。 |

### 6.2 签到

```http
POST /user/sign
GET /user/sign/count
```

两个接口都需要登录。

### 6.3 UV 统计

```http
POST /stats/uv
GET /stats/uv/site?date=2026-06-11
GET /stats/uv/shop/{shopId}?date=2026-06-11
```

`POST /stats/uv` 可由前端埋点调用，也可由后端拦截器自动记录。

## 7. 类与模块设计建议

```text
com.tars.spotai.controller
  ShopController
  SignController 或 UserController
  StatsController

com.tars.spotai.service
  ShopService
  SignService
  UvStatsService

com.tars.spotai.repository
  ShopRepository
  SignRepository
```

职责说明：

| 类 | 职责 |
|---|---|
| `ShopService` | 普通商户分页、附近商户 GEO 查询、GEO 预热。 |
| `SignService` | 签到 BitMap 写入、连续签到统计。 |
| `UvStatsService` | UV 写入和统计查询。 |
| `StatsController` | 提供统计查询接口。 |

## 8. Redis Key 汇总

| Key | 类型 | 说明 |
|---|---|---|
| `shop:geo:{typeId}` | GEO | 按分类存储商户经纬度。 |
| `sign:{userId}:{yyyyMM}` | BitMap | 用户某月签到记录。 |
| `uv:site:{yyyyMMdd}` | HyperLogLog | 全站日 UV。 |
| `uv:shop:{shopId}:{yyyyMMdd}` | HyperLogLog | 商户详情日 UV。 |
| `uv:blog:{blogId}:{yyyyMMdd}` | HyperLogLog | 探店笔记日 UV。 |
| `uv:page:{pageCode}:{yyyyMMdd}` | HyperLogLog | 自定义页面日 UV。 |

## 9. 测试方案

### 9.1 附近商户

- 商户 GEO 预热后，Redis 中存在 `shop:geo:{typeId}`。
- 传入 `x/y` 时，按距离升序返回商户。
- 不传 `x/y` 时，退化为 MySQL 分类分页。
- Redis GEO 返回 ID 后，MySQL 查询结果能按 Redis 顺序重排。
- 商户坐标修改后，GEO 数据同步更新。

### 9.2 用户签到

- 用户首次签到写入 BitMap。
- 重复签到不会重复增加状态。
- 当天未签到时连续签到数为 0。
- 连续签到 1 天、3 天、跨月边界等场景结果正确。
- 不同用户、不同月份的签到 key 互不影响。

### 9.3 UV 统计

- 同一个用户多次 `PFADD` 后 UV 仍计为 1。
- 多个用户访问后 `PFCOUNT` 增加。
- 不同日期 key 互不影响。
- 全站 UV 和商户 UV 使用不同 key，互不影响。

## 10. 实现顺序建议

1. 扩展 `ShopRepository.findAll/findByType`。
2. 在应用启动或管理接口中完成 `shop:geo:{typeId}` 预热。
3. 实现 `/shop/of/type`，支持普通分类分页和附近商户 GEO 查询。
4. 实现 `SignService`，完成 `/user/sign` 和 `/user/sign/count`。
5. 实现 `UvStatsService`，完成 UV 写入和查询。
6. 根据运营需求决定是否把签到和 UV 明细异步写入 MySQL 行为日志。

## 11. 设计结论

| 能力 | 第一版推荐方案 |
|---|---|
| 附近商户 | Redis GEO，按 `typeId` 拆分 key，商户详情仍查 MySQL。 |
| 商户坐标来源 | `tb_shop.x/y`。 |
| 签到 | Redis BitMap，key 为 `sign:{userId}:{yyyyMM}`。 |
| 连续签到 | `BITFIELD` 读取当月 1 号到当天，再从低位统计连续 1。 |
| 签到持久化 | 第一版可不落库；补签、积分和审计需要写 `tb_sign`。 |
| UV | Redis HyperLogLog，按站点、商户、笔记、页面维度拆 key。 |
| UV 精度 | 适合估算，不适合强精确计费或审计。 |

