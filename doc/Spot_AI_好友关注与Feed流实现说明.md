# Spot AI 好友关注与 Feed 流实现说明

## 1. 文档目的

本文说明 Spot AI 后续实现好友关注模块时的后端设计方案，覆盖：

- 关注与取消关注。
- 判断是否已关注。
- 查询共同关注。
- 好友关注 Feed 流的推送、读取和滚动分页。
- 数据一致性、并发控制、Redis Key 设计和测试方案。

本文是实现指导文档。当前后端已按本文完成第一版实现：关注/取消关注、是否关注、共同关注、发布探店笔记后推送 Feed、关注流滚动分页。

## 2. 业务目标

好友关注模块连接用户关系和探店内容，核心目标是：

1. 登录用户可以关注或取消关注其他用户。
2. 用户进入他人主页时，可以判断自己是否已关注该用户。
3. 用户可以查看与某个用户的共同关注。
4. 被关注用户发布探店笔记后，系统把笔记推送到粉丝的收件箱，形成关注 Feed 流。
5. Feed 流支持按时间滚动分页，避免传统分页在高频新增内容时出现重复或漏读。

## 3. 数据表设计

### 3.1 现有关注关系表

当前 SQL 中已经存在 `tb_follow`：

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | bigint | 主键，建议使用 `RedisIdWorker.nextId("follow")` 生成。 |
| `user_id` | bigint unsigned | 发起关注的用户 ID。 |
| `follow_user_id` | bigint unsigned | 被关注的用户 ID。 |
| `create_time` | timestamp | 关注时间。 |

语义说明：

- `user_id` 是“我”。
- `follow_user_id` 是“我关注的人”。
- 一条记录表示 `user_id` 关注了 `follow_user_id`。

建议补充唯一索引和查询索引：

```sql
alter table tb_follow add unique key uk_user_follow (user_id, follow_user_id);
alter table tb_follow add index idx_follow_user (follow_user_id, user_id);
```

原因：

- `uk_user_follow` 防止重复关注。
- `idx_follow_user` 用于查询某个作者的粉丝列表，发布探店笔记时需要把笔记推送给粉丝。

### 3.2 用户扩展信息表

当前 SQL 中 `tb_user_info_0`、`tb_user_info_1` 已经有：

| 字段 | 说明 |
|---|---|
| `fans` | 粉丝数量。 |
| `followee` | 当前用户关注的人数。 |

第一版可以先只维护 `tb_follow`，后续再异步统计 `fans` 和 `followee`。如果要实时展示粉丝数和关注数，可以在关注/取消关注事务中同步更新这两个计数字段。

## 4. Redis Key 设计

| Key | 类型 | 说明 |
|---|---|---|
| `follows:{userId}` | Set | 当前用户关注的用户 ID 集合。 |
| `followers:{userId}` | Set | 当前用户的粉丝 ID 集合，可选，用于加速推送。 |
| `feed:user:{userId}` | ZSet | 用户的关注 Feed 收件箱，member 为 `blogId`，score 为推送时间戳。 |
| `lock:follow:{userId}:{followUserId}` | String 或 Redisson Lock | 关注/取消关注并发保护，可选。 |

设计说明：

- `follows:{userId}` 用于快速判断是否关注、共同关注交集计算。
- `followers:{userId}` 可以减少发布笔记时查询 MySQL 粉丝列表的压力。
- `feed:user:{userId}` 使用 ZSet 支持按时间倒序读取，适合滚动分页。
- Redis 是查询加速层，MySQL `tb_follow` 仍然是关注关系的最终事实来源。

## 5. 接口设计

### 5.1 关注或取消关注

```http
PUT /follow/{id}/{isFollow}
Authorization: Bearer {token}
```

参数：

| 参数 | 位置 | 类型 | 说明 |
|---|---|---|---|
| `id` | Path | long | 被关注用户 ID。 |
| `isFollow` | Path | boolean | `true` 表示关注，`false` 表示取消关注。 |

响应：

```json
{
  "success": true,
  "data": null,
  "errorMsg": null
}
```

业务规则：

- 不能关注自己。
- 被关注用户必须存在。
- 重复关注应保持幂等，返回成功或明确提示“已关注”均可，建议返回成功。
- 重复取消关注也应保持幂等，避免前端重复点击造成异常。

### 5.2 判断是否已关注

```http
GET /follow/or/not/{id}
Authorization: Bearer {token}
```

响应：

```json
{
  "success": true,
  "data": true,
  "errorMsg": null
}
```

查询优先级：

1. 优先查 Redis `sismember follows:{currentUserId} {id}`。
2. Redis 未命中或未预热时，查 MySQL `tb_follow`。
3. 可在登录后或第一次查询后懒加载当前用户关注集合到 Redis。

### 5.3 查询共同关注

```http
GET /follow/common/{id}
Authorization: Bearer {token}
```

响应：

```json
{
  "success": true,
  "data": [
    {
      "id": 1001,
      "nickName": "可可今天不吃肉",
      "icon": "/imgs/icons/kkjtbcr.jpg"
    }
  ],
  "errorMsg": null
}
```

实现方式：

```text
sinter follows:{currentUserId} follows:{targetUserId}
```

拿到交集用户 ID 后，查询用户表并组装 `UserDTO`。如果 Redis 中不存在某个用户的关注集合，可以先从 MySQL 查询并写入 Redis，再执行交集。

### 5.4 查询关注 Feed 流

```http
GET /blog/of/follow?lastId={lastId}&offset={offset}
Authorization: Bearer {token}
```

参数：

| 参数 | 类型 | 说明 |
|---|---|---|
| `lastId` | long | 上一次查询结果中最小的时间戳，第一次请求可传当前时间毫秒值。 |
| `offset` | int | 与 `lastId` 相同时间戳的数据偏移量，第一次请求传 `0`。 |

响应：

```json
{
  "success": true,
  "data": {
    "list": [
      {
        "id": 9001,
        "userId": 1001,
        "title": "一家很舒服的小店",
        "images": "http://localhost:9000/spotai/blog/a.jpg",
        "liked": 12,
        "isLike": false,
        "name": "可可今天不吃肉",
        "icon": "/imgs/icons/kkjtbcr.jpg",
        "createTime": "2026-06-11T18:00:00"
      }
    ],
    "minTime": 1781172000000,
    "offset": 1
  },
  "errorMsg": null
}
```

建议新增响应 DTO：

```text
ScrollResult<T>
  list: List<T>
  minTime: Long
  offset: Integer
```

## 6. 关注与取消关注实现流程

### 6.1 关注流程

1. 从 `UserHolder` 获取当前登录用户。
2. 校验 `followUserId` 合法，并且不能等于当前用户 ID。
3. 查询被关注用户是否存在。
4. 写入 MySQL `tb_follow`。
5. 写入 Redis：
   - `sadd follows:{currentUserId} {followUserId}`
   - 可选：`sadd followers:{followUserId} {currentUserId}`
6. 可选更新用户扩展计数：
   - 当前用户 `followee + 1`
   - 被关注用户 `fans + 1`

推荐使用数据库唯一索引保证幂等：

```sql
insert into tb_follow (id, user_id, follow_user_id, create_time)
values (?, ?, ?, now());
```

如果发生唯一键冲突，说明已经关注，可以直接返回成功。

### 6.2 取消关注流程

1. 从 `UserHolder` 获取当前登录用户。
2. 删除 MySQL 关注关系：

```sql
delete from tb_follow
where user_id = ?
  and follow_user_id = ?;
```

3. 删除 Redis 关注集合：
   - `srem follows:{currentUserId} {followUserId}`
   - 可选：`srem followers:{followUserId} {currentUserId}`
4. 如果 MySQL 删除行数为 1，可选更新计数：
   - 当前用户 `followee - 1`
   - 被关注用户 `fans - 1`

计数更新应加条件，避免小于 0：

```sql
update tb_user_info_x
set followee = followee - 1
where user_id = ?
  and followee > 0;
```

## 7. Feed 流实现方案

Feed 流常见模式有三种：

| 模式 | 说明 | 优点 | 缺点 | 适用场景 |
|---|---|---|---|---|
| 拉模式 | 查询时根据关注列表实时拉取作者内容。 | 写入简单。 | 查询慢，关注很多时 SQL 复杂。 | 低活跃、小规模系统。 |
| 推模式 | 作者发布内容时，提前推送到粉丝收件箱。 | 读取快，用户体验好。 | 发布时写扩散，粉丝多时压力大。 | 普通社交 Feed。 |
| 推拉结合 | 普通用户推，高粉用户拉。 | 兼顾读写压力。 | 系统复杂度高。 | 大规模社交平台。 |

Spot AI 第一版建议使用推模式：

- 当前项目用户规模较小。
- Feed 读取路径更常用，需要更快。
- Redis ZSet 能很好支撑收件箱和滚动分页。

## 8. 发布探店笔记时推送 Feed

当前探店笔记发布流程是：

```text
POST /blog -> 写入 tb_blog -> 返回 blogId
```

加入 Feed 后，流程变为：

```text
POST /blog
  -> 写入 tb_blog
  -> 查询作者粉丝列表
  -> 将 blogId 推送到每个粉丝的 feed:user:{fanId}
  -> 返回 blogId
```

Redis 写入：

```text
zadd feed:user:{fanId} {nowMillis} {blogId}
```

粉丝列表来源有两种：

1. 查 MySQL：

```sql
select user_id
from tb_follow
where follow_user_id = ?;
```

2. 查 Redis：

```text
smembers followers:{authorId}
```

第一版建议：

- 以 MySQL 查询为准，保证不依赖 Redis 是否预热。
- 如果后续粉丝量增大，再维护 `followers:{authorId}` 作为推送加速集合。

## 9. Feed 滚动分页

### 9.1 为什么不用普通分页

普通分页：

```text
page=1&pageSize=10
page=2&pageSize=10
```

问题是 Feed 流中会不断插入新内容。用户读取第一页后，如果有新笔记插入，第二页的偏移会变化，容易出现重复或漏读。

### 9.2 滚动分页查询

Redis ZSet 查询：

```text
zrevrangebyscore feed:user:{userId} {lastId} 0 withscores limit {offset} {pageSize}
```

含义：

- `lastId`：本次查询允许的最大 score。
- `offset`：跳过与 `lastId` 同分的前 N 条数据。
- `pageSize`：每页数量，建议 5 或 10。

返回后计算：

- `minTime`：本次结果中最小 score。
- `offset`：本次结果里 score 等于 `minTime` 的元素数量。

下一次请求：

```text
GET /blog/of/follow?lastId={minTime}&offset={offset}
```

这样即使有新笔记插入，也不会影响用户继续向下翻旧内容。

## 10. 共同关注实现方案

共同关注依赖 Redis Set：

```text
follows:{currentUserId}
follows:{targetUserId}
```

查询步骤：

1. 确保两个用户的关注集合已经加载到 Redis。
2. 执行 `SINTER follows:{currentUserId} follows:{targetUserId}`。
3. 查询交集用户 ID 对应的用户基本信息。
4. 返回 `UserDTO` 列表。

如果 Redis 集合较大，可以限制返回数量，例如最多 20 个共同关注。

## 11. 数据一致性设计

### 11.1 MySQL 与 Redis 更新顺序

关注关系的事实来源是 MySQL，因此建议顺序：

```text
先写 MySQL -> 再更新 Redis
```

原因：

- MySQL 事务能力强，可以依赖唯一索引保证幂等。
- Redis 更新失败时，可以删除缓存或等待下次懒加载修复。

### 11.2 Redis 更新失败处理

如果 MySQL 写入成功但 Redis 更新失败：

1. 记录错误日志。
2. 删除当前用户关注集合缓存：

```text
del follows:{currentUserId}
```

3. 下次判断关注或共同关注时，从 MySQL 重新加载。

如果维护 `followers:{followUserId}`，也应删除该集合，避免粉丝集合不准确：

```text
del followers:{followUserId}
```

### 11.3 Feed 推送失败处理

发布探店笔记时，Feed 推送失败不应影响笔记发布成功。推荐策略：

- 笔记发布以 MySQL 写入 `tb_blog` 成功为准。
- Feed 推送失败时记录日志。
- 后续可以通过 Kafka 异步推送 Feed，失败后重试。

生产化方案：

```text
发布探店笔记
  -> 写 tb_blog
  -> 发送 Kafka 事件 blog.created
  -> FeedConsumer 消费事件
  -> 查询粉丝
  -> 写 feed:user:{fanId}
```

Kafka 方案优点：

- 发布接口响应更快。
- Feed 推送失败可重试。
- 高粉用户写扩散不会阻塞主请求。

## 12. 并发与幂等

### 12.1 重复关注

问题：用户连续点击关注按钮，可能产生重复记录。

解决：

- MySQL 增加唯一索引 `uk_user_follow(user_id, follow_user_id)`。
- 插入冲突时返回成功或“已关注”。

### 12.2 重复取消关注

问题：重复取消关注可能导致计数多次减少。

解决：

- 以 MySQL `delete` 影响行数判断是否真的取消成功。
- 只有影响行数为 1 时才更新计数。
- Redis `srem` 天然幂等，可以重复执行。

### 12.3 关注自己

必须在业务层拦截：

```text
currentUserId == followUserId -> 返回 “不能关注自己”
```

### 12.4 高粉用户 Feed 写扩散

如果某个用户粉丝很多，发布笔记时同步推送会变慢。

第一版：

- 直接同步写 Redis，简单可控。

后续优化：

- 使用 Kafka 异步推送。
- 对高粉用户采用推拉结合：不写入所有粉丝收件箱，读取时再拉取高粉作者内容。

## 13. 建议类与模块设计

```text
com.tars.spotai.controller
  FollowController

com.tars.spotai.service
  FollowService
  FeedService

com.tars.spotai.repository
  FollowRepository

com.tars.spotai.dto
  ScrollResultDTO<T>
```

职责说明：

| 类 | 职责 |
|---|---|
| `FollowController` | 接收关注、取消关注、判断关注、共同关注请求。 |
| `FollowService` | 处理关注关系的 MySQL 写入、Redis Set 更新和共同关注查询。 |
| `FeedService` | 处理探店笔记发布后的 Feed 推送和 Feed 滚动分页查询。 |
| `FollowRepository` | 封装 `tb_follow` 的增删查。 |
| `BlogService` | 发布探店笔记成功后调用 `FeedService.pushBlogToFollowers`。 |

## 14. 测试方案

### 14.1 关注与取消关注

- 未登录关注返回 401。
- 关注自己返回业务失败。
- 被关注用户不存在返回业务失败。
- 首次关注写入 `tb_follow`，并写入 `follows:{userId}`。
- 重复关注不会产生重复数据。
- 取消关注删除 `tb_follow`，并从 Redis Set 移除。
- 重复取消关注不会导致计数小于 0。

### 14.2 判断是否关注

- Redis 命中时直接返回。
- Redis 未命中时查 MySQL 并回填缓存。
- 未关注返回 `false`。
- 已关注返回 `true`。

### 14.3 共同关注

- 两个用户存在共同关注时返回对应 `UserDTO`。
- 无共同关注时返回空列表。
- Redis Set 为空时能从 MySQL 重建。

### 14.4 Feed 推送

- 作者发布笔记后，粉丝的 `feed:user:{fanId}` 出现该 `blogId`。
- 非粉丝不会收到该笔记。
- 多个粉丝都能收到同一篇笔记。
- Feed 推送失败不影响笔记发布成功。

### 14.5 Feed 滚动分页

- 第一次查询返回最新 Feed。
- 第二次使用 `minTime` 和 `offset` 可以继续查询旧内容。
- 多条 Feed score 相同时不会重复或漏读。
- Redis 无 Feed 数据时返回空列表。

## 15. 实现顺序建议

建议按以下顺序实现：

1. 新增 `FollowRepository`，完成 `tb_follow` 增删查。
2. 新增 `FollowService` 和 `FollowController`，实现关注、取消关注、判断关注。
3. 使用 Redis Set 实现共同关注。
4. 新增 `ScrollResultDTO`。
5. 在 `BlogService.saveBlog` 成功后推送 Feed。
6. 实现 `GET /blog/of/follow` 滚动分页。
7. 补充单元测试和必要的集成测试。
8. 后续再考虑 Kafka 异步推送和高粉用户推拉结合。

## 16. 设计结论

| 能力 | 第一版方案 |
|---|---|
| 关注关系事实来源 | MySQL `tb_follow`。 |
| 防重复关注 | MySQL 唯一索引 `uk_user_follow`。 |
| 判断是否关注 | Redis Set `follows:{userId}`，未命中回源 MySQL。 |
| 共同关注 | Redis `SINTER`。 |
| Feed 模式 | 推模式。 |
| Feed 存储 | Redis ZSet `feed:user:{userId}`。 |
| Feed 分页 | `zrevrangebyscore` 滚动分页，返回 `minTime` 和 `offset`。 |
| 发布推送 | 第一版同步推送，后续可改 Kafka 异步推送。 |
| 高粉用户优化 | 后续使用推拉结合。 |
