# Spot AI 评论 RAG 总结实现说明

## 1. 目标

为商户详情页提供“AI 总结店铺评价”能力。用户进入商家详情页面后，可以看到基于真实评论生成的摘要，例如：

- 总体评价：用户普遍认可的点和主要槽点。
- 高频关键词：口味、环境、服务、价格等。
- 推荐理由：适合哪些消费场景。
- 风险提示：排队久、价格偏高、服务不稳定等。

第一版只做“按商户生成评论总结”，不做用户个性化推荐、不做多轮问答。

## 2. 技术选型

| 模块 | 方案 |
|---|---|
| 总结模型 | DeepSeek API |
| 模型接入方式 | Spring AI DeepSeek Chat 或 OpenAI-compatible 配置 |
| 向量化模型 | 阿里云 DashScope EmbeddingModel |
| 向量检索 | Redis Stack + Spring AI RedisVectorStore |
| 向量持久化 | MySQL 保存评论向量文本、元数据和索引状态 |
| 原始数据 | `tb_review`、`tb_shop` |
| 摘要缓存 | Redis String |
| 前端展示 | 商家详情页新增 AI 总结部件 |

说明：

- DeepSeek 只负责根据检索到的评论上下文生成中文总结。
- 阿里云 DashScope 只负责把评论内容转换为向量。
- Redis Stack 负责在线向量相似度检索。
- MySQL 负责保存向量化后的评论数据和索引状态，便于重建 Redis 向量索引。
- 密钥、Base URL 等敏感信息只放在 `local-secrets.properties` 或环境变量中，不提交到 Git。

参考文档：

- DeepSeek API: https://api-docs.deepseek.com/
- Spring AI DeepSeek Chat: https://docs.spring.io/spring-ai/reference/api/chat/deepseek-chat.html
- Spring AI Alibaba DashScope Embeddings: https://java2ai.com/integration/rag/embeddings/dashscope-embeddings
- Spring AI Redis VectorStore: https://docs.spring.io/spring-ai/reference/api/vectordbs/redis.html
- Redis Vector Search: https://redis.io/docs/latest/develop/ai/search-and-query/vectors/

## 3. 数据流程

### 3.1 评论入库后构建知识片段

评论写入 `tb_review` 后，生成一条 RAG 文档：

```text
商户ID：{shopId}
评分：{score}
评论内容：{content}
评论时间：{createTime}
```

文档元数据：

| 字段 | 说明 |
|---|---|
| `reviewId` | 评论 ID |
| `shopId` | 商户 ID |
| `score` | 评论评分 |
| `createTime` | 评论时间 |

### 3.2 向量化

使用阿里云 DashScope `EmbeddingModel` 将评论内容转成向量。

向量化结果写入两处：

1. MySQL：保存评论向量、文本、元数据、索引状态。
2. Redis Stack：写入 `RedisVectorStore`，用于实时相似度检索。

第一版可以提供一个手动重建入口：

```text
读取 tb_review 中 status = 0 的评论
-> 调用 DashScope EmbeddingModel 生成向量
-> 写入 MySQL 向量表
-> 写入 Redis Stack 向量索引
```

### 3.3 生成总结

当请求某个商户的评论总结时：

1. 根据 `shopId` 和总结问题从 Redis Stack 检索相关评论。
2. 将检索结果作为上下文传给 DeepSeek。
3. 要求 DeepSeek 输出结构化中文摘要。
4. 将结果缓存到 Redis，避免每次访问都调用模型。

推荐 Prompt：

```text
你是本地生活点评分析助手。
请只基于给定评论总结，不要编造评论中不存在的信息。

输出格式：
1. 总体评价：一句话总结。
2. 亮点：3 条以内。
3. 槽点：3 条以内，没有则写“暂无明显槽点”。
4. 适合场景：2 条以内。
```

## 4. 向量数据存储设计

### 4.1 Redis Stack

Redis Stack 保存在线检索需要的数据：

```text
index: review_vector_idx
key prefix: review:vector:
metadata: reviewId, shopId, score, createTime
content: 评论文本
embedding: 评论向量
```

检索时必须带 `shopId` 过滤，避免不同商户评论混入同一次总结。

### 4.2 MySQL 向量表

新增表 `tb_review_vector`：

```sql
create table tb_review_vector (
  id bigint not null primary key,
  review_id bigint not null,
  shop_id bigint not null,
  content text not null,
  embedding_json json not null,
  embedding_model varchar(64) not null,
  redis_indexed tinyint not null default 0,
  create_time timestamp not null default current_timestamp,
  update_time timestamp not null default current_timestamp on update current_timestamp,
  unique key uk_review_id (review_id),
  key idx_shop_id (shop_id),
  key idx_redis_indexed (redis_indexed)
);
```

职责：

- 保存向量化后的原始结果，避免重复调用 Embedding 模型。
- Redis Stack 数据丢失时，可从 MySQL 重建索引。
- 记录 `embedding_model`，方便后续模型升级后判断是否需要重建。

## 5. 接口设计

### 5.1 查询商户评论 AI 总结

```http
GET /review/summary?shopId={shopId}
```

响应示例：

```json
{
  "success": true,
  "data": {
    "shopId": 1,
    "summary": "整体评价较好，用户主要认可口味和环境。",
    "highlights": ["菜品稳定", "环境适合聚餐", "性价比较高"],
    "weaknesses": ["高峰期可能需要排队"],
    "scenes": ["朋友聚餐", "周末家庭用餐"],
    "reviewCount": 36,
    "generatedAt": "2026-06-24T14:30:00"
  },
  "errorMsg": null
}
```

### 5.2 重建商户评论向量

第一版建议做内部接口，便于开发验证：

```http
POST /admin/review/summary/reindex?shopId={shopId}
```

后续可改为定时任务或 MQ 消费。

## 6. 后端类设计

```text
com.tars.spotai.controller
  ReviewSummaryController

com.tars.spotai.service
  ReviewSummaryService
  ReviewRagIndexService

com.tars.spotai.dto
  ReviewSummaryDTO

com.tars.spotai.entity
  ReviewVector

com.tars.spotai.repository
  ReviewVectorRepository

com.tars.spotai.config
  AiConfig
```

职责：

| 类 | 职责 |
|---|---|
| `ReviewSummaryController` | 暴露评论总结接口 |
| `ReviewSummaryService` | 编排缓存、检索、DeepSeek 总结 |
| `ReviewRagIndexService` | 评论向量化，并同步 MySQL 与 Redis Stack |
| `ReviewVectorRepository` | 读写 `tb_review_vector` |
| `ReviewRepository` | 补充按商户查询待索引评论的方法 |
| `AiConfig` | 配置 DeepSeek ChatModel、DashScope EmbeddingModel、RedisVectorStore |

## 7. 配置项

`application.yml` 只保留非敏感默认配置：

```yaml
spring:
  ai:
    model:
      chat: deepseek
      embedding: dashscope

spotai:
  ai:
    review-summary:
      top-k: 20
      cache-ttl-minutes: 60
      min-review-count: 3
      redis-index: review_vector_idx
```

`local-secrets.properties` 保存密钥和敏感连接信息：

```properties
DEEPSEEK_API_KEY=你的DeepSeek API Key
DASHSCOPE_API_KEY=你的阿里云DashScope API Key

spring.ai.deepseek.api-key=${DEEPSEEK_API_KEY}
spring.ai.deepseek.chat.options.model=deepseek-chat
spring.ai.dashscope.api-key=${DASHSCOPE_API_KEY}

REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=你的Redis密码
MYSQL_PASSWORD=你的MySQL密码
```

如果当前 Spring AI 版本未直接支持 DeepSeek starter，也可以使用 OpenAI-compatible 方式接入：

```properties
spring.ai.openai.api-key=${DEEPSEEK_API_KEY}
spring.ai.openai.base-url=https://api.deepseek.com
spring.ai.openai.chat.options.model=deepseek-chat
```

## 8. 缓存策略

Redis Key：

```text
review:summary:{shopId}
```

缓存内容为 `ReviewSummaryDTO` JSON，默认 60 分钟过期。

失效策略：

- 评论新增或状态变化后，删除 `review:summary:{shopId}`。
- 评论向量可以异步更新，短时间内允许最终一致。
- Redis Stack 向量索引异常时，从 `tb_review_vector` 重建。

## 9. 前端展示设计

在商家详情页面中新增一个 AI 总结部件，放在店铺基础信息之后、评论列表之前。

部件内容：

| 区域 | 展示内容 |
|---|---|
| 标题 | `AI 总结店铺评价` |
| 状态 | 加载中、生成成功、评论不足、生成失败 |
| 总体评价 | `summary` |
| 亮点 | `highlights` 标签列表 |
| 槽点 | `weaknesses` 标签列表 |
| 适合场景 | `scenes` 标签列表 |
| 更新时间 | `generatedAt` |

前端请求：

```text
商家详情加载成功
-> 调用 GET /review/summary?shopId={selectedShop.id}
-> 展示 AI 总结部件
```

前端状态建议：

- 加载中：展示骨架屏或“正在分析近期评价...”。
- 评论不足：展示“当前评价较少，暂不生成 AI 总结”。
- 失败：展示“AI 总结暂不可用”，不影响原评论列表。
- 成功：展示摘要、亮点、槽点和适合场景。

## 10. 第一版实施步骤

1. 增加 DeepSeek Chat、DashScope Embedding、Redis VectorStore 相关依赖。
2. 部署 Redis Stack，确认 Redis 支持向量检索。
3. 新增 `tb_review_vector` 表。
4. 增加 `ReviewSummaryDTO` 和 `ReviewVector`。
5. 扩展 `ReviewRepository`，支持按 `shopId` 查询有效评论。
6. 实现 `ReviewRagIndexService`，将评论向量写入 MySQL 和 Redis Stack。
7. 实现 `ReviewSummaryService`，完成 Redis 检索、Prompt 拼接和 DeepSeek 总结。
8. 增加 `GET /review/summary` 接口。
9. 前端商家详情页新增 AI 总结部件。
10. 增加 Redis 摘要缓存，降低模型调用频率。

## 11. 注意事项

- DeepSeek 总结必须只基于检索到的评论，Prompt 中明确禁止编造。
- 评论数量少于 `min-review-count` 时，直接返回“评论数量不足，暂不生成总结”。
- 模型调用失败时返回友好提示，不影响原评论列表展示。
- API Key、Redis 密码、MySQL 密码只放在 `local-secrets.properties` 或环境变量中。
- MySQL 中保存的向量数据可能较大，后续需要根据评论量评估归档和清理策略。

## 12. 第一版结论

第一版采用“评论入库 -> 阿里云向量化 -> MySQL 持久化 -> Redis Stack 检索 -> DeepSeek 总结 -> 前端展示”的方案。该方案把模型生成、向量化、在线检索和持久化职责拆开，便于本地开发、线上重建索引和后续扩展“商户问答”“个性化推荐理由”“差评原因分析”。
