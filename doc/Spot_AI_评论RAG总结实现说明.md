# Spot AI 评论 RAG 总结实现说明

> 核对日期：2026-07-14  
> 核对范围：`ReviewService`、`ReviewSummaryService`、`ReviewRagIndexService`、`ReviewSummaryRefreshScheduler`、`SpotAiChatTools`、SQL。

## 1. 目标

评论 RAG 总结用于在店铺详情页和 AI 对话中快速展示“这家店评价怎么样”。当前目标不是每次用户提问都全量向量化，而是：

```text
评价变更 -> 标记摘要过期 -> 后台生成 RAG 摘要 -> 写 MySQL + Redis -> 前端/AI 只读摘要
```

当用户进入店铺详情时，如果没有摘要，后端会尝试立即生成。

## 2. 技术选型

| 能力 | 当前实现 |
| --- | --- |
| 聊天模型 | DeepSeek OpenAI 兼容 API |
| 向量模型 | DashScope `text-embedding-v3` |
| 向量检索 | Redis Stack / Spring AI `RedisVectorStore` |
| 向量元数据 | `tb_review_embedding` |
| 摘要持久化 | `tb_review_summary` |
| 摘要缓存 | Redis `review:summary:{shopId}` |
| 前端展示 | 店铺详情页 `AI 评论分析` 卡片 |
| AI 工具 | `queryReviewSummary`、`recommendShops` |

敏感信息放在 `local-secrets.properties` 或环境变量中。

## 3. 数据表

### 3.1 `tb_review`

保存用户评价，是 RAG 原始数据来源。

### 3.2 `tb_review_image`

保存评价图片。

### 3.3 `tb_review_embedding`

保存评论分片与向量索引元数据，当前不使用旧文档中的 `tb_review_vector`。

关键字段：

- `review_id`
- `shop_id`
- `chunk_index`
- `chunk_text`
- `embedding_id`
- `redis_indexed`
- `status`

### 3.4 `tb_review_summary`

保存每家店铺的摘要结果。

关键字段：

- `shop_id`
- `status`
- `summary`
- `highlights_json`
- `weaknesses_json`
- `scenes_json`
- `review_count`
- `generated_at`
- `version`

状态：

| 状态 | 含义 |
| --- | --- |
| `READY` | 摘要可用 |
| `INSUFFICIENT_REVIEWS` | 评价数量不足 |
| `UNAVAILABLE` | AI 未启用或生成失败 |
| `STALE` | 评论发生变化，等待重建 |
| `BUILDING` | 正在生成 |

## 4. 生成流程

### 4.1 评价变更

发布或删除评价后：

1. `ReviewService` 写入或更新 `tb_review`。
2. 调用 `ReviewSummaryService.markStale(shopId)`。
3. 删除 Redis `review:summary:{shopId}`。
4. 通过 MQ 或 fallback 触发摘要刷新。

### 4.2 后台扫描

`ReviewSummaryRefreshScheduler` 定时扫描：

- 没有摘要的店铺。
- 状态为 `STALE` 的店铺。

扫描到后批量生成摘要，保证每家有评价的店铺尽量都有摘要。

### 4.3 用户进入详情页

前端调用：

```http
GET /review/summary?shopId={shopId}
```

如果 MySQL/Redis 已有可用摘要，直接返回；如果没有摘要且评价数量足够，则尝试立即生成。

## 5. RAG 索引流程

`ReviewRagIndexService` 负责：

1. 查询 `tb_review` 中未索引的有效评价。
2. 生成评论分片。
3. 调用 DashScope embedding。
4. 写入 Redis Stack 向量索引。
5. 写入或更新 `tb_review_embedding` 元数据。

Redis Stack 索引配置：

```yaml
spotai:
  ai:
    review-summary:
      index-name: review_vector_idx
      key-prefix: "review:vector:"
```

## 6. 摘要生成

`SpringAiReviewSummaryGenerator` 根据 Redis 向量检索结果和 LLM 生成结构化摘要：

- 总结文本 `summary`
- 亮点 `highlights`
- 槽点 `weaknesses`
- 适合场景 `scenes`

结果写入 `tb_review_summary`，并缓存到 Redis。

## 7. 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/review/summary?shopId={shopId}` | 查询店铺评论摘要 |
| `POST` | `/admin/review/summary/reindex` | 管理接口，重建索引或摘要 |

AI 工具：

- `queryReviewSummary(shopId)`
- `recommendShops(...)` 会尽量读取摘要作为推荐理由。

## 8. 配置

```yaml
spotai:
  ai:
    review-summary:
      enabled: ${SPOTAI_AI_ENABLED:false}
      top-k: ${REVIEW_AI_TOP_K:20}
      min-review-count: ${REVIEW_AI_MIN_REVIEW_COUNT:3}
      cache-ttl-minutes: ${REVIEW_AI_CACHE_TTL_MINUTES:60}
      refresh-scan-delay-ms: ${REVIEW_AI_REFRESH_SCAN_DELAY_MS:60000}
      refresh-scan-initial-delay-ms: ${REVIEW_AI_REFRESH_SCAN_INITIAL_DELAY_MS:10000}
      refresh-batch-size: ${REVIEW_AI_REFRESH_BATCH_SIZE:30}
```

## 9. 前端展示

店铺详情页中：

- 评价列表和 AI 摘要分开加载。
- 摘要慢不会阻塞评价列表展示。
- 摘要不可用时展示“准备中 / 暂不可用 / 评价不足”等状态。
- 用户可点击“问 AI”，让 AI 基于当前店铺上下文继续分析。

## 10. 当前边界

- `SPOTAI_AI_ENABLED=false` 时不会真正调用模型，会写入 `UNAVAILABLE`。
- Redis Stack 不可用时向量检索和生成会失败，但业务评价仍可正常展示。
- 摘要没有按时间过期机制；主要靠评价变更标记 `STALE` 和后台扫描重建。
- 评论摘要是辅助决策，不替代原始用户评价。
