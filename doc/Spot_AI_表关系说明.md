# Spot AI 表关系说明

> 核对日期：2026-07-14  
> 核对范围：`sql/spotai_0.sql`、`sql/spotai_1.sql`、`sql/migrate_*.sql`、Repository 实际查询。

## 1. 数据库定位

当前项目使用 MySQL 保存业务数据。`spotai_0` 和 `spotai_1` 两个库脚本结构基本一致，部分表使用 `_0/_1` 后缀做分表，部分表为普通表。

当前主 SQL 中不存在以下旧文档曾提到的表：

- `tb_user_phone_0/1`
- `tb_user_info_0/1`
- `tb_blog_comments`
- `tb_review_like`
- `tb_review_ai_analysis`
- `tb_review_vector`
- `tb_ai_tool_confirm_0/1`
- `tb_ai_recommend_log_0/1`
- `tb_user_preference_0/1`
- `tb_user_behavior_log_0/1`
- `tb_sign`

## 2. 当前表清单

| 领域 | 表 |
| --- | --- |
| 用户 | `tb_user_0`、`tb_user_1`、`tb_user_email_0`、`tb_user_email_1` |
| 商户 | `tb_shop`、`tb_shop_type`、`tb_shop_item` |
| 内容 | `tb_blog`、`tb_follow` |
| 评价 | `tb_review`、`tb_review_image` |
| 评论 RAG | `tb_review_embedding`、`tb_review_summary` |
| 优惠券 | `tb_voucher_0`、`tb_voucher_1`、`tb_seckill_voucher_0`、`tb_seckill_voucher_1` |
| 订单 | `tb_voucher_order_0`、`tb_voucher_order_1`、`tb_voucher_order_router_0`、`tb_voucher_order_router_1` |
| 对账补偿 | `tb_voucher_reconcile_log_0`、`tb_voucher_reconcile_log_1`、`tb_rollback_failure_log` |
| AI | `tb_ai_conversation_0`、`tb_ai_conversation_1`、`tb_ai_user_memory_0`、`tb_ai_user_memory_1`、`tb_ai_tool_call_log_0`、`tb_ai_tool_call_log_1` |

`tb_shop_item` 由 `sql/migrate_shop_items.sql` 创建，种子数据位于 `sql/seed_shop_items.sql`。

## 3. 用户域

| 表 | 关键字段 | 说明 |
| --- | --- | --- |
| `tb_user_0/1` | `id`、`email`、`password`、`nick_name`、`icon` | 用户主表，当前登录使用邮箱 |
| `tb_user_email_0/1` | `user_id`、`email` | 邮箱到用户 ID 的映射 |

路由规则由 `ShardUtils` 和 `UserRepository` 控制。登录流程会先按邮箱查映射，不存在则自动注册用户并写入用户表和邮箱映射表。

## 4. 商户域

| 表 | 关键字段 | 说明 |
| --- | --- | --- |
| `tb_shop_type` | `id`、`name`、`icon`、`sort` | 商户分类 |
| `tb_shop` | `id`、`name`、`type_id`、`area`、`address`、`x`、`y`、`avg_price`、`sold`、`comments`、`score`、`open_hours` | 商户主表 |
| `tb_shop_item` | `id`、`shop_id`、`name`、`description`、`price`、`sort` | 店铺菜品/服务 |

关系：

- `tb_shop.type_id` 对应 `tb_shop_type.id`。
- `tb_shop_item.shop_id` 对应 `tb_shop.id`。
- `tb_shop.x/y` 可预热到 Redis GEO，用于附近商户查询。

## 5. 内容与关注

| 表 | 关键字段 | 说明 |
| --- | --- | --- |
| `tb_blog` | `id`、`shop_id`、`user_id`、`title`、`images`、`content`、`liked`、`comments` | 探店笔记 |
| `tb_follow` | `id`、`user_id`、`follow_user_id`、`create_time` | 用户关注关系 |

关系：

- `tb_blog.user_id` 对应发布者。
- `tb_blog.shop_id` 对应被探店商户。
- `tb_follow.user_id` 是关注发起人。
- `tb_follow.follow_user_id` 是被关注用户。

点赞明细在 Redis ZSet 中维护，MySQL `tb_blog.liked` 保存计数。

## 6. 评价与 RAG

| 表 | 关键字段 | 说明 |
| --- | --- | --- |
| `tb_review` | `id`、`shop_id`、`user_id`、`score`、`content`、`status`、`liked` | 店铺用户评价 |
| `tb_review_image` | `id`、`review_id`、`image_url`、`sort` | 评价图片 |
| `tb_review_embedding` | `review_id`、`shop_id`、`chunk_text`、`embedding_id`、`redis_indexed` | 评论向量元数据 |
| `tb_review_summary` | `shop_id`、`status`、`summary`、`highlights_json`、`weaknesses_json`、`scenes_json` | 店铺评论摘要 |

关系：

- `tb_review.shop_id` 对应店铺。
- `tb_review.user_id` 对应评价用户。
- `tb_review_image.review_id` 对应评价。
- `tb_review_embedding.review_id` 对应评价分片。
- `tb_review_summary.shop_id` 每店一条摘要状态。

## 7. 优惠券与订单

| 表 | 关键字段 | 说明 |
| --- | --- | --- |
| `tb_voucher_0/1` | `id`、`shop_id`、`title`、`pay_value`、`actual_value`、`type`、`status` | 普通券和秒杀券主表 |
| `tb_seckill_voucher_0/1` | `voucher_id`、`init_stock`、`stock`、`begin_time`、`end_time` | 秒杀券扩展表 |
| `tb_voucher_order_0/1` | `id`、`user_id`、`voucher_id`、`status` | 优惠券订单 |
| `tb_voucher_order_router_0/1` | `order_id`、`user_id`、`voucher_id` | 订单路由表 |
| `tb_voucher_reconcile_log_0/1` | `order_id`、`voucher_id`、`before_qty`、`change_qty`、`after_qty`、`trace_id` | 库存对账日志 |
| `tb_rollback_failure_log` | `voucher_id`、`user_id`、`order_id`、`trace_id`、`detail` | Redis 或库存回滚失败记录 |

关系：

- `tb_voucher_0/1.shop_id` 对应店铺。
- `tb_seckill_voucher_0/1.voucher_id` 对应券主表。
- `tb_voucher_order_0/1.voucher_id` 对应券。
- `tb_voucher_order_router_0/1.order_id` 用于定位订单。

## 8. AI 表

| 表 | 说明 |
| --- | --- |
| `tb_ai_conversation_0/1` | 登录用户 AI 对话历史，包含 `metadata` 保存工具调用等信息 |
| `tb_ai_user_memory_0/1` | 用户长期偏好记忆 |
| `tb_ai_tool_call_log_0/1` | AI 工具调用审计和确认状态 |

当前中风险工具确认复用 `tb_ai_tool_call_log_0/1` 的 `confirm_token` 和 `status` 字段，没有单独的 `tb_ai_tool_confirm` 表。

## 9. 阅读顺序

1. 先看 `tb_shop_type`、`tb_shop`、`tb_shop_item`，理解商户展示。
2. 再看 `tb_user_0/1`、`tb_user_email_0/1`，理解登录。
3. 然后看 `tb_blog`、`tb_follow`、`tb_review`，理解内容与评价。
4. 再看 `tb_voucher_*`、`tb_voucher_order_*`，理解优惠券。
5. 最后看 `tb_review_summary`、`tb_review_embedding`、`tb_ai_*`，理解 AI/RAG。
