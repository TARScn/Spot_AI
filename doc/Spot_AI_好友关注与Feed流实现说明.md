# Spot AI 好友关注与 Feed 流实现说明

> 核对日期：2026-07-14  
> 核对范围：`FollowController`、`FollowService`、`FeedService`、`BlogService`、`BlogPublishedConsumer`、SQL。

## 1. 当前功能

当前已实现：

- 关注/取消关注：`PUT /follow/{id}/{isFollow}`
- 判断是否已关注：`GET /follow/or/not/{id}`
- 共同关注：`GET /follow/common/{id}`
- 关注流：`GET /blog/of/follow?lastId=...&offset=...`
- 探店笔记发布后投递给粉丝 Feed。

前端探店笔记页已提供“发现 / 关注”切换。“发现”展示全部笔记，“关注”展示已关注用户发布的笔记。

## 2. 数据表

| 表 | 说明 |
| --- | --- |
| `tb_follow` | 关注关系，字段包括 `user_id`、`follow_user_id`、`create_time` |
| `tb_blog` | 探店笔记，字段包括 `shop_id`、`user_id`、`title`、`images`、`content` |

当前 SQL 中没有 `tb_user_info` 表，粉丝数和关注数不在 MySQL 用户资料表中冗余存储。

## 3. Redis Key

| Key | 类型 | 说明 |
| --- | --- | --- |
| `follows:{userId}` | Set | 用户关注的人 |
| `followers:{userId}` | Set | 用户粉丝 |
| `feed:user:{userId}` | ZSet | 关注 Feed 收件箱，member 为 blogId，score 为发布时间 |

MySQL `tb_follow` 是事实来源，Redis 用于加速查询和 Feed 推送。

## 4. 关注流程

关注：

1. 校验登录和目标用户。
2. 禁止关注自己。
3. 写入 `tb_follow`。
4. 写入 Redis `follows:{userId}` 和 `followers:{targetUserId}`。

取消关注：

1. 删除 `tb_follow`。
2. 删除 Redis 中对应关注和粉丝关系。

重复关注和重复取消需要幂等处理。

## 5. 关注流

发布笔记时：

1. `BlogService` 写入 `tb_blog`。
2. 通过 `MqEventPublisher` 发布 `BlogPublishedMessage`。
3. MQ 关闭或发送失败时同步执行 fallback。
4. `FeedService` 查询粉丝列表。
5. 将笔记 ID 写入每个粉丝的 `feed:user:{followerId}`。

关注流查询使用滚动分页：

```http
GET /blog/of/follow?lastId={lastId}&offset={offset}
```

返回中会包含下一次请求需要的 `minTime` 和 `offset`。

## 6. 为什么用滚动分页

关注 Feed 是 ZSet，score 可能重复。如果只用页码分页，新增笔记会导致翻页错位。当前实现使用：

- `lastId`：上一页最后一批数据的最小 score。
- `offset`：相同 score 中已经跳过的数量。

这样可以稳定地向下滚动加载。

## 7. 共同关注

`GET /follow/common/{id}` 会计算当前用户和目标用户共同关注的人。

实现思路：

1. 优先读取 Redis `follows:{userId}`。
2. Redis 不完整时回源 MySQL `tb_follow`。
3. 对两个用户的关注集合求交集。
4. 根据用户 ID 查询公开用户信息。

## 8. 与 MQ 的关系

关注功能本身直接写 MySQL 和 Redis。笔记发布后的 Feed 投递可以走 MQ：

- MQ 开启：`BlogPublishedConsumer` 异步投递。
- MQ 关闭或发送失败：`MqEventPublisher` 同步执行投递。

## 9. 当前边界

- 当前没有粉丝数/关注数的独立统计表。
- 高粉用户写扩散可能较重，后续可改为推拉结合。
- Redis Feed 是加速层，必要时可以从 `tb_follow + tb_blog` 回补。
- 当前没有独立的私信、好友申请或黑名单能力。
