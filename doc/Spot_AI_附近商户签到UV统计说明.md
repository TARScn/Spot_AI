# Spot AI 附近商户、用户签到与 UV 统计实现说明

> 核对日期：2026-07-14  
> 核对范围：`ShopService`、`SignService`、`UvStatsService`、`StatsController`、`RedisConstants`。

## 1. 附近商户

### 1.1 数据来源

附近商户依赖 `tb_shop`：

- `id`
- `type_id`
- `x`：经度
- `y`：纬度
- `score`
- `avg_price`
- `sold`

### 1.2 Redis GEO

GEO Key：

```text
shop:geo:{typeId}
```

预热接口：

```http
PUT /shop/geo/load
```

预热逻辑：

1. 查询 `tb_shop`。
2. 按 `type_id` 分组。
3. 写入 Redis GEO。
4. member 为 `shopId`，坐标来自 `x/y`。

### 1.3 查询接口

```http
GET /shop/of/type?typeId=1&current=1&x=108.953&y=34.265
```

传入 `x/y` 时，优先使用 Redis GEO 查询附近店铺；不传经纬度时按普通分类分页查询。

## 2. 用户签到

### 2.1 当前实现

签到当前只使用 Redis BitMap，不落 MySQL。

Key：

```text
sign:{userId}:{yyyyMM}
```

接口：

| 方法 | 路径 | 登录 | 说明 |
| --- | --- | --- | --- |
| `POST` | `/user/sign` | 是 | 今日签到 |
| `GET` | `/user/sign/count` | 是 | 当月连续签到天数 |

### 2.2 签到流程

1. 从 `UserHolder` 获取当前用户。
2. 计算当天在当月的 offset：`dayOfMonth - 1`。
3. 调用 Redis `SETBIT`。
4. 如果原 bit 已经是 1，则返回“今日已签到”。

### 2.3 连续签到统计

`SignService` 使用 Redis `BITFIELD` 读取从月初到今天的 bit，然后从低位开始统计连续 1 的数量。

当前主 SQL 中没有 `tb_sign`，如果后续需要补签、积分审计或运营报表，可以新增签到流水表。

## 3. UV 统计

### 3.1 数据结构

UV 使用 Redis HyperLogLog。

| 维度 | Key |
| --- | --- |
| 全站 | `uv:site:{yyyyMMdd}` |
| 店铺 | `uv:shop:{shopId}:{yyyyMMdd}` |
| 笔记 | `uv:blog:{blogId}:{yyyyMMdd}` |
| 页面 | `uv:page:{pageCode}:{yyyyMMdd}` |

### 3.2 记录接口

```http
POST /stats/uv
Content-Type: application/json
```

请求示例：

```json
{
  "targetType": "shop",
  "targetId": 1,
  "pageCode": null,
  "visitor": "anonymous-device-id"
}
```

`visitor` 可以是登录用户 ID、设备 ID 或前端生成的匿名访客 ID。

### 3.3 查询接口

```http
GET /stats/uv/site?date=2026-07-14
GET /stats/uv/shop/{shopId}?date=2026-07-14
```

## 4. MQ 与同步 fallback

UV 记录可以通过 `MqEventPublisher` 发出 `UvRecordMessage`：

- MQ 开启：`UvRecordConsumer` 异步写 HyperLogLog。
- MQ 关闭或发送失败：同步写 HyperLogLog。

这意味着本地开发不启动 RocketMQ 时，UV 功能仍可用。

## 5. 当前边界

- 附近商户依赖 Redis GEO 预热；如果未预热，可能退化为普通查询。
- 签到不落库，Redis 数据丢失会影响签到记录。
- HyperLogLog 是近似统计，不适合需要精确去重的财务或计费场景。
- UV 当前不做复杂反作弊，只依赖 visitor 去重。
