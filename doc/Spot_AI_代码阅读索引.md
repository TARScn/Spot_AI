# Spot AI 代码阅读索引

本文档用于快速定位当前项目的核心业务代码，减少后续人工查阅时的重复搜索。

## 前端入口

- `web/src/main.jsx`：当前前端主入口，包含首页、店铺详情、探店笔记、优惠券、我的页面和 AI 助手。
- `web/src/styles.css`：页面全局样式。
- `web/src/mockLocalLifeData.js`：后端不可用时的首页 mock 数据。

## 后端核心业务

- `BlogController` / `BlogService`：探店笔记发布、发现流、关注流、点赞、删除。
- `ReviewController` / `ReviewService`：店铺评价发布、分页查询、删除，以及评价摘要刷新触发。
- `VoucherController` / `VoucherOrderController` / `VoucherService`：代金券、秒杀活动、领券下单。
- `AiChatController` / `SpringAiChatService` / `SpotAiChatTools`：AI 对话、工具调用、店铺推荐、领券确认。
- `ReviewSummaryService` / `ReviewSummaryRefreshScheduler`：店铺评价 AI 摘要的查询、生成和后台扫描刷新。
- `UploadController` / `FileStorageService`：图片上传与 MinIO 存储。

## 数据库脚本

- `sql/spotai_0.sql`、`sql/spotai_1.sql`：当前初始化表结构和基础数据。
- `sql/migrate_drop_unused_tables.sql`：清理当前后端未使用的历史遗留表。
- `doc/Spot_AI_数据库表清理记录.md`：本次表清理的判断依据和保留/删除清单。

## 当前阅读建议

1. 看页面问题时，先从 `web/src/main.jsx` 的状态分区注释定位模块。
2. 看笔记问题时，先看 `BlogController` 的接口路径，再进入 `BlogService`。
3. 看评价和摘要问题时，重点看 `ReviewService#markSummaryStaleAndRefreshAfterCommit`，不要绕过事务提交后的刷新逻辑。
4. 看 AI 工具问题时，先确认 `SpringAiChatService` 是否提前处理了意图，再看 `SpotAiChatTools` 的具体工具实现。
