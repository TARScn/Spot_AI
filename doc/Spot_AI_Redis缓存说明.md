# Spot AI Redis 缓存说明

> 核对日期：2026-07-14  
> 核对范围：`RedisConstants`、`CacheClient`、`UserService`、`ShopService`、`BlogService`、`VoucherService`、`SignService`、`UvStatsService`、RAG 相关配置。

## 1. Redis 使用范围

当前项目使用 Redis / Redis Stack 承担以下能力：

| 场景 | Key 前缀 | 实现类 |
| --- | --- | --- |
| 邮箱验证码 | `login:code:{email}` | `UserService` |
| 登录态 Token | `login:token:{token}` | `UserService`、`RefreshTokenInterceptor` |
| 店铺详情缓存 | `cache:shop:{shopId}` | `ShopService`、`CacheClient` |
| 店铺缓存重建锁 | `lock:shop:{shopId}` | `CacheClient` |
| 秒杀库存 | `seckill:stock:{voucherId}` | `VoucherService` |
| 秒杀已下单用户 | `seckill:order:{voucherId}` | `VoucherService` |
| 用户订单锁 | `lock:order:{userId}:{voucherId}` | `VoucherService` |
| 笔记点赞明细 | `blog:liked:{blogId}` | `BlogService` |
| 用户点赞笔记 | `blog:liked:user:{userId}` | `BlogService` |
| 关注集合 | `follows:{userId}` | `FollowService` |
| 粉丝集合 | `followers:{userId}` | `FollowService` |
| 关注 Feed | `feed:user:{userId}` | `FeedService` |
| 店铺 GEO | `shop:geo:{typeId}` | `ShopService` |
| 签到 BitMap | `sign:{userId}:{yyyyMM}` | `SignService` |
| 全站 UV | `uv:site:{yyyyMMdd}` | `UvStatsService` |
| 店铺 UV | `uv:shop:{shopId}:{yyyyMMdd}` | `UvStatsService` |
| 笔记 UV | `uv:blog:{blogId}:{yyyyMMdd}` | `UvStatsService` |
| 页面 UV | `uv:page:{pageCode}:{yyyyMMdd}` | `UvStatsService` |
| 评论摘要缓存 | `review:summary:{shopId}` | `ReviewSummaryService` |
| 评论向量索引 | `review:vector:*` / `review_vector_idx` | `ReviewAiConfig`、`ReviewRagIndexService` |

## 2. 缓存工具类

`CacheClient` 封装了三类常见缓存模式：

- 普通 TTL 缓存：`set`。
- 空值缓存：用于缓存穿透保护。
- 逻辑过期缓存：用于店铺详情，过期后异步重建。

店铺详情当前通过 `ShopService` 读取：

1. 查 Redis `cache:shop:{shopId}`。
2. 命中且未逻辑过期则直接返回。
3. 未命中或过期时回源 MySQL `tb_shop`。
4. 重建期间用 `lock:shop:{shopId}` 防止并发击穿。

## 3. 登录缓存

当前登录方式是邮箱验证码，不是手机号验证码。

```text
login:code:{email} -> 6 位验证码，TTL 由 spotai.auth.code-ttl-minutes 控制
login:token:{token} -> 用户 Hash，TTL 由 spotai.auth.token-ttl-minutes 控制
```

`RefreshTokenInterceptor` 每次请求会读取 `login:token:{token}` 并刷新 TTL。

## 4. 店铺 GEO

`/shop/geo/load` 会把 `tb_shop.x/y` 预热到 Redis GEO：

```text
shop:geo:{typeId}
member = shopId
longitude = tb_shop.x
latitude = tb_shop.y
```

`GET /shop/of/type?typeId=...&x=...&y=...` 在传入经纬度时优先使用 Redis GEO 做附近查询。

## 5. 签到

签到只使用 Redis BitMap，不落 `tb_sign`：

```text
sign:{userId}:{yyyyMM}
```

`POST /user/sign` 将当天 bit 置为 1；`GET /user/sign/count` 通过 `BITFIELD` 计算当月连续签到天数。

## 6. 点赞与关注 Feed

笔记点赞：

- `blog:liked:{blogId}`：ZSet，member 为 userId，score 为时间戳。
- `blog:liked:user:{userId}`：ZSet，member 为 blogId，score 为时间戳。
- MySQL `tb_blog.liked` 保存计数。

关注关系：

- MySQL `tb_follow` 是事实来源。
- Redis `follows:{userId}`、`followers:{userId}` 做查询加速。
- `feed:user:{userId}` 是关注 Feed 收件箱。

## 7. 秒杀券

秒杀券抢单使用 Lua 原子脚本：

1. 检查 `seckill:stock:{voucherId}` 是否大于 0。
2. 检查 `seckill:order:{voucherId}` 是否已包含当前用户。
3. 成功则库存 `decr`，用户加入 Set。

如果订单落库失败，`VoucherService` 会回滚 Redis 库存和用户下单标记，并在必要时写入 `tb_rollback_failure_log`。

## 8. 评论 RAG

评论 RAG 使用 Redis Stack 向量索引：

- 索引名：`review_vector_idx`
- Key 前缀：`review:vector:`
- 元数据表：`tb_review_embedding`
- 摘要表：`tb_review_summary`
- 摘要缓存：`review:summary:{shopId}`

摘要本体以 MySQL 为准，Redis 只做缓存和向量检索加速。

## 9. 配置

主要配置位于 `application.yml`：

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD}

spotai:
  ai:
    review-summary:
      vector-redis:
        host: ${VECTOR_REDIS_HOST:localhost}
        port: ${VECTOR_REDIS_PORT:6380}
        password: ${VECTOR_REDIS_PASSWORD:${REDIS_PASSWORD:}}
```

本地如果 Redis Stack 跑在 WSL，需要确保 Windows 能访问对应端口。

## 10. 当前边界

- `tb_sign` 已从当前主 SQL 中移除，签到暂不做 MySQL 审计。
- 点赞用户明细主要在 Redis，MySQL 只保存计数。
- Redis Stack 向量索引可由 `tb_review_embedding` 元数据重建。
- 生产环境不要把 Redis 端口暴露到公网。
