# Spot AI 探店功能实现说明

> 核对日期：2026-07-14  
> 核对范围：`BlogController`、`BlogService`、`BlogRepository`、`UploadController`、前端探店笔记页。

## 1. 当前功能

当前探店笔记模块已实现：

- 发布探店笔记。
- 上传笔记图片到 MinIO。
- 查询最近笔记。
- 查询热门笔记。
- 查询我的笔记。
- 查询指定用户笔记。
- 查询指定店铺笔记。
- 查询关注用户笔记。
- 查看笔记详情。
- 点赞/取消点赞。
- 查询点赞用户。
- 删除自己的笔记。

前端探店笔记页支持“发现 / 关注”切换，发布区域默认隐藏，通过加号按钮展开。

## 2. 数据表

| 表 | 说明 |
| --- | --- |
| `tb_blog` | 探店笔记主体 |
| `tb_follow` | 关注关系，支持关注流 |

当前主 SQL 中没有 `tb_blog_comments`，笔记评论功能未实现。

## 3. Redis Key

| Key | 类型 | 说明 |
| --- | --- | --- |
| `blog:liked:{blogId}` | ZSet | 某篇笔记的点赞用户 |
| `blog:liked:user:{userId}` | ZSet | 某个用户点赞过的笔记 |
| `feed:user:{userId}` | ZSet | 关注 Feed 收件箱 |

MySQL `tb_blog.liked` 保存点赞计数，Redis 保存点赞用户明细和实时状态。

## 4. 接口

| 方法 | 路径 | 登录 | 说明 |
| --- | --- | --- | --- |
| `POST` | `/blog` | 是 | 发布探店笔记 |
| `GET` | `/blog/hot` | 否 | 热门笔记 |
| `GET` | `/blog/recent` | 否 | 最新笔记 |
| `GET` | `/blog/of/me` | 是 | 我的笔记 |
| `GET` | `/blog/liked/me` | 是 | 我点赞过的笔记 |
| `GET` | `/blog/of/user` | 否 | 指定用户笔记 |
| `GET` | `/blog/of/shop` | 否 | 指定店铺笔记 |
| `GET` | `/blog/of/follow` | 是 | 关注流 |
| `GET` | `/blog/{id}` | 否 | 笔记详情 |
| `PUT` | `/blog/like/{id}` | 是 | 点赞/取消点赞 |
| `DELETE` | `/blog/{id}` | 是 | 删除自己的笔记 |
| `GET` | `/blog/likes/{id}` | 否 | 点赞用户列表 |

## 5. 发布流程

1. 前端搜索并确认店铺，避免直接从固定下拉列表选择旧数据。
2. 选择图片后调用 `/upload/blog` 上传到 MinIO。
3. 前端提交标题、正文、图片 URL、店铺 ID。
4. `BlogService` 校验登录、店铺存在和内容。
5. 使用 `RedisIdWorker` 生成笔记 ID。
6. 写入 `tb_blog`。
7. 发布 `BlogPublishedMessage`，用于关注 Feed 投递。
8. MQ 关闭或发送失败时同步投递。

## 6. 点赞流程

点赞使用 Redis Lua 脚本保证状态切换和用户点赞列表更新一致：

- 未点赞：加入 `blog:liked:{blogId}` 和 `blog:liked:user:{userId}`，MySQL `liked + 1`。
- 已点赞：从两个 ZSet 删除，MySQL `liked - 1`。

如果 MySQL 更新失败，会执行 Redis 回滚脚本。

## 7. 关注流

发布笔记后，系统会把笔记 ID 写入粉丝的 `feed:user:{followerId}`。

查询接口使用滚动分页：

```http
GET /blog/of/follow?lastId={lastId}&offset={offset}
```

## 8. 删除规则

`DELETE /blog/{id}` 当前只允许作者删除自己的笔记。

删除后：

- `tb_blog` 状态按当前 Repository 逻辑处理。
- Redis 点赞集合会清理。
- 前端“我的”页面和探店笔记页会刷新列表。

## 9. 当前边界

- 笔记评论未实现。
- 笔记点赞明细主要依赖 Redis，没有独立 `tb_blog_like` 表。
- 图片物理删除依赖 MinIO 删除接口；笔记删除不保证自动清理历史图片。
- 高粉用户 Feed 写扩散后续可优化为推拉结合。
