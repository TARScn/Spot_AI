# Spot AI 技术文档

> 核对日期：2026-07-14  
> 核对范围：当前仓库代码、SQL、前端页面、Docker 配置。

## 1. 项目定位

Spot AI 是一个单体式本地生活点评平台示例项目，核心目标是模拟“大众点评 / 本地生活”业务：

```text
找店 -> 看详情 -> 看评价 -> 领优惠 -> 写评价/笔记 -> AI 辅助推荐
```

当前不是微服务拆分项目，后端为一个 Spring Boot 应用；前端为一个 React/Vite 应用。

## 2. 技术栈

### 2.1 前端

- React 18
- Vite 5
- lucide-react
- marked
- 原生 CSS 移动优先布局

### 2.2 后端

- Java 17
- Spring Boot 3.4
- Spring AI 1.0
- Spring AI Alibaba DashScope
- MyBatis-Plus / JDBC
- Redis / Redisson
- RocketMQ Spring Boot Starter
- MinIO Java SDK

### 2.3 存储

- MySQL：业务数据、AI 记忆、评论摘要。
- Redis Stack：缓存、GEO、BitMap、HyperLogLog、向量索引。
- MinIO：图片对象存储。
- RocketMQ：可选异步事件。

## 3. 后端模块

| 模块 | 入口 | 说明 |
| --- | --- | --- |
| 用户 | `UserController`、`UserService` | 邮箱验证码登录、自动注册、个人资料、签到 |
| 商户 | `ShopController`、`ShopService` | 店铺详情、搜索、分类、GEO 附近查询 |
| 菜品/服务 | `ShopItemService` | 店铺详情页展示真实菜品和服务 |
| 探店笔记 | `BlogController`、`BlogService` | 发布、列表、详情、点赞、删除 |
| 关注 Feed | `FollowService`、`FeedService` | 关注关系、关注流 |
| 评价 | `ReviewController`、`ReviewService` | 店铺评价、图片、我的评价、删除 |
| 优惠券 | `VoucherController`、`VoucherService` | 普通券、秒杀券、下单 |
| 上传 | `UploadController`、`FileStorageService` | MinIO 上传 |
| UV | `StatsController`、`UvStatsService` | HyperLogLog UV |
| AI | `AiChatController`、`SpringAiChatService` | AI 对话、工具调用、记忆 |
| RAG | `ReviewSummaryService`、`ReviewRagIndexService` | 评论向量索引和店铺摘要 |

## 4. 前端页面

前端当前主要集中在 `web/src/main.jsx`：

- 首页：搜索、分类、推荐商家、筛选、榜单。
- 店铺详情：优惠、推荐菜/服务、AI 评论总结、写评价、用户评价下滑加载。
- 探店笔记：发现/关注、发布笔记、图片上传、点赞、关注。
- 优惠页面：优惠券和秒杀活动。
- 我的页面：用户资料、我的笔记、我的评价、AI 记忆和历史。
- AI 助手：Markdown 回复、店铺链接、确认卡片。

## 5. 认证与用户

当前登录方式是邮箱验证码：

1. `POST /user/code?email=...` 发送验证码。
2. 验证码写入 Redis `login:code:{email}`。
3. `POST /user/login` 校验验证码。
4. 用户不存在时自动注册。
5. Token 写入 Redis `login:token:{token}`。
6. 前端后续请求通过 `Authorization` 传 token。

当前没有 JWT，也没有 Sa-Token。

## 6. AI 与 RAG

| 能力 | 当前实现 |
| --- | --- |
| 对话模型 | DeepSeek OpenAI 兼容 API |
| 向量模型 | DashScope embedding |
| 工具调用 | Spring AI `@Tool` |
| 长期记忆 | MySQL `tb_ai_user_memory_*` |
| 对话历史 | MySQL `tb_ai_conversation_*` |
| 工具确认 | `tb_ai_tool_call_log_*` + 前端确认卡片 |
| 评论向量元数据 | `tb_review_embedding` |
| 评论摘要 | `tb_review_summary` + Redis `review:summary:{shopId}` |

可用 AI 工具：

- `searchShop`
- `queryShopDetail`
- `recommendShops`
- `queryReviewSummary`
- `queryCoupons`
- `claimCoupon`

## 7. MQ 事件

当前 MQ 是可选能力：

- `spotai.mq.enabled`
- `spotai.voucher.mq-enabled`

相关事件：

- 评论摘要刷新。
- 商户变更。
- UV 记录。
- 笔记发布后 Feed 投递。
- 普通代金券领取。
- 秒杀券下单。

如果 MQ 关闭或发送失败，`MqEventPublisher` 会执行同步 fallback，核心流程不中断。

## 8. 文件上传

图片上传使用 MinIO：

- `POST /upload/file`
- `POST /upload/blog`
- `DELETE /upload/file`

店铺评价和探店笔记图片都会通过 MinIO 保存，返回可公开访问的 URL。

## 9. 当前边界

- 当前项目是单体应用，不是微服务架构。
- 没有 Elasticsearch；店铺搜索主要基于 MySQL 字段过滤，附近查询用 Redis GEO。
- 没有 JWT；登录态保存在 Redis。
- 没有独立支付系统；优惠券领取/秒杀成功即生成订单。
- RocketMQ 不在生产 compose 中，需要额外部署。
