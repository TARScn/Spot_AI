# Spot AI 代码阅读索引

> 核对日期：2026-07-14  
> 用途：帮助人工快速找到当前项目真实实现入口。

## 1. 前端入口

| 文件 | 说明 |
| --- | --- |
| `web/src/main.jsx` | 首页、店铺详情、探店笔记、优惠券、我的、AI 助手主入口 |
| `web/src/styles.css` | 全局样式、移动端布局、详情抽屉、AI 聊天气泡 |
| `web/src/mockLocalLifeData.js` | 前端兜底 mock 数据 |
| `web/src/shopNavigation.js` | 店铺链接、榜单和 19 位 ID 处理 |
| `web/src/shopItems.js` | 店铺菜品/服务数据归一化 |
| `web/src/shopNavigation.test.js` | 前端核心工具测试 |

## 2. 后端 Controller

| Controller | 说明 |
| --- | --- |
| `UserController` | 邮箱验证码、登录、当前用户、用户详情、签到 |
| `ShopController` | 店铺详情、店铺菜品/服务、分类查询、搜索、GEO 预热 |
| `ShopTypeController` | 商户分类 |
| `BlogController` | 探店笔记发布、列表、详情、点赞、删除、关注流 |
| `FollowController` | 关注、取消关注、共同关注 |
| `ReviewController` | 店铺评价、我的评价、发布评价、删除评价、评论摘要 |
| `VoucherController` | 活动列表、店铺券列表、新增普通券、新增秒杀券 |
| `VoucherOrderController` | 普通券领取、秒杀券下单 |
| `UploadController` | MinIO 文件上传和删除 |
| `StatsController` | UV 写入和查询 |
| `AiChatController` | AI 对话、历史、记忆、工具确认 |
| `ReviewSummaryAdminController` | 评论摘要重建管理接口 |

## 3. 后端核心 Service

| Service | 说明 |
| --- | --- |
| `UserService` | 邮箱验证码登录、自动注册、Token 写入 Redis |
| `ShopService` | 店铺缓存、搜索、GEO 查询 |
| `ShopItemService` | 店铺菜品/服务查询 |
| `BlogService` | 探店笔记、点赞、删除、点赞用户 |
| `FeedService` | 关注 Feed 收件箱 |
| `FollowService` | 关注关系和 Redis 加速 |
| `ReviewService` | 店铺评价、图片、删除、触发摘要刷新 |
| `ReviewSummaryService` | 评论摘要读取、生成、MySQL + Redis 缓存 |
| `ReviewRagIndexService` | 评论向量化与 Redis Stack 索引 |
| `VoucherService` | 普通券领取、秒杀、MQ fallback、库存回滚 |
| `SpringAiChatService` | AI 对话协调器 |
| `SpotAiChatTools` | AI 可调用工具 |
| `MqEventPublisher` | MQ 发送与同步 fallback |

## 4. Repository 与数据表

| Repository | 主要表 |
| --- | --- |
| `UserRepository` | `tb_user_0/1`、`tb_user_email_0/1` |
| `ShopRepository` | `tb_shop` |
| `ShopItemRepository` | `tb_shop_item` |
| `BlogRepository` | `tb_blog` |
| `FollowRepository` | `tb_follow` |
| `ReviewRepository` | `tb_review`、`tb_review_image` |
| `ReviewEmbeddingRepository` | `tb_review_embedding` |
| `ReviewSummaryRepository` | `tb_review_summary` |
| `VoucherRepository` | `tb_voucher_*`、`tb_seckill_voucher_*`、`tb_voucher_order_*` |
| `AiConversationRepository` | `tb_ai_conversation_*` |
| `AiUserMemoryRepository` | `tb_ai_user_memory_*` |
| `AiToolCallLogRepository` | `tb_ai_tool_call_log_*` |

## 5. 配置入口

| 文件 | 说明 |
| --- | --- |
| `spotai/src/main/resources/application.yml` | 后端主配置 |
| `spotai/local-secrets.properties.example` | 本地敏感配置模板 |
| `web/vite.config.js` | 前端开发代理 |
| `docker-compose.prod.yml` | 生产容器编排 |
| `deploy.env.example` | Docker 部署环境变量模板 |

## 6. SQL 入口

| 文件 | 说明 |
| --- | --- |
| `sql/create_database.sql` | 创建 `spotai_0`、`spotai_1` |
| `sql/spotai_0.sql`、`sql/spotai_1.sql` | 当前主表结构和基础数据 |
| `sql/migrate_review_summary_store.sql` | 评论摘要表 |
| `sql/migrate_ai_agent_memory.sql` | AI 长期记忆表 |
| `sql/migrate_ai_tool_call_log.sql` | AI 工具调用日志 |
| `sql/migrate_shop_items.sql` | 店铺菜品/服务表 |
| `sql/seed_shop_items.sql` | 店铺菜品/服务种子数据 |

## 7. 建议阅读顺序

1. 从 `web/src/main.jsx` 看页面和接口调用。
2. 对照 Controller 找后端入口。
3. 沿 Service 阅读业务规则。
4. 到 Repository 确认真实 SQL 和表。
5. 最后阅读 AI、RAG、MQ 文档理解跨模块能力。
