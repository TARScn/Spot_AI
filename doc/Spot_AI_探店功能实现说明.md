# Spot AI 探店功能实现说明

## 1. 文档目的

本文说明 Spot AI 后续实现探店笔记模块时的技术方案，覆盖：

- 探店笔记发布功能。
- 探店笔记查看功能。
- 探店笔记点赞和取消点赞功能。
- 探店笔记点赞排行榜功能。

当前文档只说明实现思路和解决方案，不直接编写功能代码。

## 2. 业务目标

探店笔记是用户围绕商户发布的内容，连接用户、商户和社交互动。第一版目标是完成核心闭环：

1. 登录用户发布探店笔记。
2. 用户可以查看笔记详情、热门笔记、个人笔记列表。
3. 登录用户可以点赞或取消点赞。
4. 笔记详情页展示最近点赞用户排行榜。

第一版不实现评论、关注流、图片上传和内容审核，但数据结构和接口预留扩展空间。

## 3. 当前相关表

### 3.1 探店笔记表

`tb_blog` 保存探店笔记主体内容。

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | bigint | 笔记 ID。 |
| `shop_id` | bigint | 被探店商户 ID。 |
| `user_id` | bigint | 发布用户 ID。 |
| `title` | varchar | 标题。 |
| `images` | varchar | 图片地址，最多 9 张，使用逗号分隔。 |
| `content` | varchar | 探店正文。 |
| `liked` | int | 点赞数量。 |
| `comments` | int | 评论数量。 |
| `create_time` | timestamp | 创建时间。 |
| `update_time` | timestamp | 更新时间。 |

建议后续补充索引：

```sql
alter table tb_blog add index idx_user_time (user_id, create_time);
alter table tb_blog add index idx_shop_time (shop_id, create_time);
alter table tb_blog add index idx_liked_time (liked, create_time);
```

### 3.2 评论表

`tb_blog_comments` 已存在，第一版暂不实现评论功能。

| 字段 | 说明 |
|---|---|
| `blog_id` | 被评论的探店笔记。 |
| `user_id` | 评论用户。 |
| `parent_id` | 一级评论 ID；为 `0` 时表示一级评论。 |
| `answer_id` | 被回复的评论 ID。 |
| `liked` | 评论点赞数。 |
| `status` | 评论状态。 |

### 3.3 关注关系表

`tb_follow` 已存在，第一版点赞排行榜不依赖关注关系。后续实现关注流 Feed 时可复用该表。

| 字段 | 说明 |
|---|---|
| `user_id` | 发起关注的用户。 |
| `follow_user_id` | 被关注的用户。 |

## 4. Redis Key 设计

| Key | 类型 | 说明 |
|---|---|---|
| `blog:liked:{blogId}` | ZSet | 记录某篇笔记的点赞用户，member 为 `userId`，score 为点赞时间戳。 |
| `blog:hot` | ZSet | 热门笔记榜，member 为 `blogId`，score 为热度分。第一版可选。 |
| `cache:blog:{blogId}` | String | 笔记详情缓存，第一版可选。 |
| `lock:blog:like:{blogId}` | String 或 Redisson Lock | 点赞并发保护，第一版优先使用 Lua，锁可作为扩展方案。 |

点赞排行榜使用 `blog:liked:{blogId}`，原因：

- ZSet 可以天然按点赞时间排序。
- `zscore` 可以快速判断当前用户是否已点赞。
- `zrange` / `zrevrange` 可以快速取最近点赞用户。
- Redis 保存实时状态，MySQL `tb_blog.liked` 保存最终计数。

## 5. 接口设计

### 5.1 发布探店笔记

```http
POST /blog
Authorization: Bearer {token}
Content-Type: application/json
```

请求体：

```json
{
  "shopId": 1,
  "title": "人均30元的宝藏茶餐厅",
  "images": "/imgs/blogs/a.jpg,/imgs/blogs/b.jpg",
  "content": "环境很好，菜品稳定，适合周末聚餐。"
}
```

响应：

```json
{
  "success": true,
  "data": 1987043235650076673,
  "errorMsg": null
}
```

校验规则：

| 字段 | 规则 |
|---|---|
| `shopId` | 必填，必须存在对应商户。 |
| `title` | 必填，建议长度 1 到 255。 |
| `images` | 必填或按产品策略允许为空；最多 9 张，逗号分隔。 |
| `content` | 必填，建议长度 1 到 2048。 |

实现流程：

1. 从 `UserHolder` 获取当前登录用户。
2. 校验商户是否存在。
3. 校验标题、图片和正文。
4. 使用 `RedisIdWorker.nextId("blog")` 生成笔记 ID。
5. 写入 `tb_blog`，`liked=0`，`comments=0`。
6. 返回笔记 ID。

### 5.2 查询探店笔记详情

```http
GET /blog/{id}
```

响应：

```json
{
  "success": true,
  "data": {
    "id": 4,
    "shopId": 4,
    "userId": 1987042234935279617,
    "title": "无尽浪漫的夜晚",
    "images": "/imgs/blogs/a.jpg,/imgs/blogs/b.jpg",
    "content": "探店正文",
    "liked": 104,
    "comments": 1,
    "isLike": true,
    "name": "可可今天不吃肉",
    "icon": "/imgs/icons/kkjtbcr.jpg",
    "createTime": "2026-06-11T10:00:00"
  },
  "errorMsg": null
}
```

实现流程：

1. 根据 `id` 查询 `tb_blog`。
2. 不存在则返回 `探店笔记不存在`。
3. 根据 `blog.user_id` 查询用户昵称和头像。
4. 如果当前请求已登录，查询 `zscore blog:liked:{blogId} userId` 判断 `isLike`。
5. 返回笔记详情。

缓存策略：

- 第一版可以直接查 MySQL。
- 如果详情访问量变高，可使用 `cache:blog:{blogId}` 缓存笔记主体。
- 点赞状态 `isLike` 不建议进入详情缓存，因为它与当前用户相关。

### 5.3 查询热门探店笔记

```http
GET /blog/hot?current=1
```

响应：

```json
{
  "success": true,
  "data": [
    {
      "id": 4,
      "title": "无尽浪漫的夜晚",
      "liked": 104,
      "name": "可可今天不吃肉",
      "icon": "/imgs/icons/kkjtbcr.jpg",
      "isLike": false
    }
  ],
  "errorMsg": null
}
```

第一版实现：

```sql
select *
from tb_blog
order by liked desc, create_time desc
limit ?, ?
```

说明：

- `current` 表示页码，默认从 1 开始。
- 每页大小建议固定为 10。
- 返回列表时仍需要填充作者昵称、头像和当前用户点赞状态。

后续优化：

- 使用 `blog:hot` ZSet 维护热度榜。
- 热度分可由点赞数、评论数和发布时间衰减共同计算。
- 适合首页热门流量较大时启用。

### 5.4 查询我的探店笔记

```http
GET /blog/of/me?current=1
Authorization: Bearer {token}
```

实现流程：

1. 从 `UserHolder` 获取当前用户 ID。
2. 查询 `tb_blog where user_id = ? order by create_time desc`。
3. 分页返回。

### 5.5 查询指定用户探店笔记

```http
GET /blog/of/user?id={userId}&current=1
```

实现流程：

1. 校验 `userId`。
2. 查询该用户发布的探店笔记。
3. 按发布时间倒序分页。

## 6. 点赞功能

### 6.1 接口设计

```http
PUT /blog/like/{id}
Authorization: Bearer {token}
```

响应：

```json
{
  "success": true,
  "data": null,
  "errorMsg": null
}
```

语义：

- 如果当前用户未点赞，则执行点赞。
- 如果当前用户已点赞，则执行取消点赞。

### 6.2 Redis + MySQL 设计

点赞状态保存在 Redis ZSet：

```text
blog:liked:{blogId}
member = userId
score = 当前时间戳毫秒
```

MySQL `tb_blog.liked` 保存点赞数量，用于排序和兜底展示。

### 6.3 点赞流程

1. 获取当前登录用户 ID。
2. 校验笔记是否存在。
3. 查询 `zscore blog:liked:{blogId} userId`。
4. 如果不存在：
   - `zadd blog:liked:{blogId} now userId`
   - `update tb_blog set liked = liked + 1 where id = ?`
5. 如果存在：
   - `zrem blog:liked:{blogId} userId`
   - `update tb_blog set liked = liked - 1 where id = ? and liked > 0`
6. 返回成功。

### 6.4 原子性方案

第一版可以在单体服务中用事务和顺序操作实现：

```text
查 Redis 点赞状态 -> 更新 MySQL liked -> 更新 Redis ZSet
```

更推荐使用 Lua 保证 Redis 判断和变更原子，再配合 MySQL 计数更新：

```lua
-- KEYS[1] = blog:liked:{blogId}
-- ARGV[1] = userId
-- ARGV[2] = nowMillis

if redis.call('zscore', KEYS[1], ARGV[1]) then
  redis.call('zrem', KEYS[1], ARGV[1])
  return -1
end

redis.call('zadd', KEYS[1], ARGV[2], ARGV[1])
return 1
```

返回值说明：

| 返回值 | 含义 | MySQL 操作 |
|---|---|---|
| `1` | 本次执行点赞 | `liked = liked + 1` |
| `-1` | 本次取消点赞 | `liked = liked - 1 where liked > 0` |

### 6.5 一致性说明

点赞功能允许短暂最终一致：

- Redis ZSet 是实时点赞状态。
- MySQL `liked` 是数量统计和排序字段。
- 如果 MySQL 更新失败，应回滚 Redis 本次变更，或记录补偿日志。
- 高并发场景下建议把点赞事件发送到 Kafka，由异步消费者批量落库。

第一版建议先同步更新 MySQL，降低系统复杂度。

## 7. 点赞排行榜功能

### 7.1 接口设计

```http
GET /blog/likes/{id}
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

### 7.2 排行榜规则

第一版展示最近点赞的前 5 个用户。

Redis 查询：

```text
zrange blog:liked:{blogId} 0 4
```

如果希望展示最新点赞用户，应使用：

```text
zrevrange blog:liked:{blogId} 0 4
```

本文推荐使用最新点赞排序：

```text
zrevrange blog:liked:{blogId} 0 4
```

### 7.3 用户信息查询

拿到用户 ID 列表后，需要查询用户昵称和头像。

注意：MySQL `in (...)` 查询不能保证返回顺序，因此需要在应用层按 Redis 返回顺序重排。

示例流程：

1. `zrevrange blog:liked:{blogId} 0 4` 得到 `[1003, 1002, 1001]`。
2. 查询用户表得到用户信息 Map。
3. 按 `[1003, 1002, 1001]` 顺序组装 `UserDTO` 列表。

### 7.4 冷启动处理

如果 Redis 中没有 `blog:liked:{blogId}`：

- 第一版返回空列表。
- 后续如果新增点赞关系表，可从 MySQL 重建排行榜。

当前 SQL 中没有独立的 `tb_blog_like` 表，因此 Redis 是点赞用户明细的唯一实时来源。为了增强可靠性，后续建议新增：

```sql
create table tb_blog_like (
  id bigint not null primary key,
  blog_id bigint unsigned not null,
  user_id bigint unsigned not null,
  create_time timestamp not null default current_timestamp,
  unique key uk_blog_user (blog_id, user_id),
  key idx_blog_time (blog_id, create_time)
);
```

新增该表后：

- Redis 负责实时查询和排行榜。
- MySQL 负责持久化点赞关系。
- 系统重启或 Redis 数据丢失时可以恢复排行榜。

## 8. 类与模块设计

建议包结构：

```text
com.tars.spotai.controller
  BlogController

com.tars.spotai.service
  BlogService

com.tars.spotai.repository
  BlogRepository

com.tars.spotai.dto
  BlogDTO
  BlogViewDTO

com.tars.spotai.entity
  Blog
```

职责说明：

| 类 | 职责 |
|---|---|
| `BlogController` | 接收 `/blog` 相关 HTTP 请求，做参数绑定。 |
| `BlogService` | 编排发布、查看、点赞和排行榜业务。 |
| `BlogRepository` | 封装 `tb_blog` 查询和更新。 |
| `UserRepository` | 复用已有用户查询能力，填充作者和点赞用户信息。 |
| `ShopRepository` | 校验商户是否存在。 |

## 9. 数据一致性与并发问题

### 9.1 重复点赞

问题：用户并发点击点赞按钮，可能重复增加点赞数。

解决：

- Redis ZSet 记录用户是否已点赞。
- 使用 Lua 原子判断并切换状态。
- MySQL 只根据 Lua 返回值做 `+1` 或 `-1`。

### 9.2 点赞数小于 0

问题：重复取消点赞可能导致 `liked` 变成负数。

解决：

```sql
update tb_blog
set liked = liked - 1
where id = ?
  and liked > 0;
```

### 9.3 Redis 与 MySQL 不一致

问题：Redis 更新成功后 MySQL 更新失败。

第一版解决：

- 捕获异常。
- 执行反向 Redis 操作回滚。
- 返回失败。

后续生产化方案：

- 点赞操作写 Kafka 事件。
- Consumer 异步更新 MySQL。
- 定时任务对比 `zcard blog:liked:{blogId}` 和 `tb_blog.liked` 并修正。

### 9.4 热点笔记

热门笔记点赞量高，单个 Redis Key 可能成为热点。

第一版通常可以接受。后续优化：

- 点赞事件进入 Kafka 削峰。
- MySQL 点赞数批量聚合更新。
- 热门榜 `blog:hot` 定时刷新，不在每次点赞时同步重排复杂热度。

## 10. 测试计划

### 10.1 发布笔记

- 未登录发布返回 `请先登录`。
- 商户不存在返回 `商户不存在`。
- 标题为空返回参数错误。
- 正常发布写入 `tb_blog`，返回笔记 ID。

### 10.2 查看笔记

- 笔记不存在返回 `探店笔记不存在`。
- 查询详情能返回作者昵称、头像。
- 当前用户已点赞时 `isLike=true`。
- 当前用户未登录或未点赞时 `isLike=false`。

### 10.3 点赞功能

- 第一次点赞后 Redis ZSet 存在用户 ID，MySQL `liked + 1`。
- 再次点赞执行取消，Redis ZSet 删除用户 ID，MySQL `liked - 1`。
- 并发点赞同一笔记时，同一用户只能增加一次点赞数。
- 取消点赞时 `liked` 不会小于 0。

### 10.4 点赞排行榜

- 多个用户点赞后，按点赞时间倒序返回前 5 个。
- 用户信息返回顺序与 Redis ZSet 顺序一致。
- Redis 无点赞数据时返回空列表。

## 11. 后续扩展

后续可继续扩展：

1. 评论发布、评论点赞和评论排行榜。
2. 关注流 Feed：用户发布笔记后推送到粉丝收件箱。
3. 图片上传、图片删除和图片安全校验。
4. 内容审核：敏感词、图片审核、用户举报。
5. AI 总结：将优质探店笔记和评论作为商户 RAG 知识源。
6. 个性化推荐：结合用户点赞、浏览、收藏和下单行为排序。

## 12. 设计结论

探店模块第一版建议采用以下策略：

| 问题 | 方案 |
|---|---|
| 笔记发布 | 登录用户写入 `tb_blog`，ID 使用 `RedisIdWorker`。 |
| 笔记查看 | 查 `tb_blog`，填充作者信息和当前用户点赞状态。 |
| 热门笔记 | 第一版按 `liked desc, create_time desc` 查询 MySQL。 |
| 点赞状态 | Redis ZSet `blog:liked:{blogId}` 保存用户点赞明细。 |
| 点赞计数 | MySQL `tb_blog.liked` 保存计数，Redis 与 MySQL 配合。 |
| 点赞排行榜 | Redis ZSet 按点赞时间倒序取前 5 个用户。 |
| 并发保护 | 使用 Redis Lua 原子切换点赞状态，MySQL 条件更新兜底。 |
| 持久化增强 | 后续建议新增 `tb_blog_like` 表保存点赞关系。 |
