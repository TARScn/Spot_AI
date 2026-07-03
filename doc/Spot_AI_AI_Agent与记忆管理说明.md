# Spot AI Agent 与记忆管理说明

## 1. 当前结论

Spot AI 采用“一入口 AI 助手 + 后端 Agent/Tool 分工 + 分层记忆”的方案。

当前版本不直接上复杂 Graph 编排，而是先用 `SpringAiChatService` 作为协调层：

- 用 `ChatClient.defaultTools(spotAiChatTools)` 让模型按需调用工具。
- 用 `PreferenceExtractorAgent` 抽取长期偏好。
- 用 `ConversationSummaryAgent` 压缩溢出的历史对话。
- 用 `AgentMemorySelectionPolicy` 控制不同路由可见的长期记忆 namespace。
- 用 `RecommendationPreferenceResolver` 将用户问题、偏好记忆、历史摘要转换为店铺推荐筛选条件。

这样可以先保证找店、评价总结、优惠查询和推荐链路稳定，再逐步迁移到 Spring AI Alibaba Multi-agent / Graph。

## 2. 官方依据

- Spring AI Alibaba README: https://github.com/alibaba/spring-ai-alibaba
- Spring AI Alibaba Multi-agent: https://java2ai.com/docs/frameworks/agent-framework/advanced/multi-agent/
- Spring AI Alibaba Agent Tool: https://java2ai.com/docs/frameworks/agent-framework/advanced/agent-tool/
- Spring AI Alibaba Memory: https://java2ai.com/docs/frameworks/agent-framework/advanced/memory/
- Spring AI Chat Memory: https://docs.spring.io/spring-ai/reference/api/chat-memory.html

核心依据：

1. Spring AI / Spring AI Alibaba 区分短期对话记忆和长期记忆。
2. `ChatMemory` 服务于当前模型调用，不等于完整聊天历史。
3. 完整历史应单独保存，用于页面展示、审计和摘要。
4. 长期记忆适合用 `namespace + key + JSON` 表达结构化偏好。
5. 多 Agent 的关键是上下文工程：不同 Agent/Tool 只看完成任务需要的信息。

## 3. 已实现分层

| 层 | 当前实现 | 用途 |
|---|---|---|
| 短期上下文 | `AiShortTermMemoryService` + Spring AI `ChatMemory` | 登录用户当前会话窗口 |
| 窗口裁剪 | `AiContextWindowService` + `MessageWindowChatMemory` | 同时控制进入 prompt 的最近消息数量和字符预算 |
| 完整历史 | `AiConversationRepository` / `tb_ai_conversation_*` | 前端历史展示、审计、摘要来源 |
| 长期偏好 | `UserMemoryStore` / `MysqlUserMemoryStore` / `tb_ai_user_memory_*` | 预算、口味、商圈、优惠偏好 |
| 历史摘要 | `conversation.summary.default` | 将溢出的旧对话压缩为可复用线索 |
| 记忆选择 | `AgentMemorySelectionPolicy` | 按路由选择可见 namespace |
| 推荐解析 | `RecommendationPreferenceResolver` | 从问题和记忆中推导推荐条件 |

## 4. 聊天调用流程

```text
用户输入
-> 识别用户登录态
-> 登录用户优先读取 ChatMemory 短期上下文
   -> 短期记忆为空/失败时回退 MySQL 完整历史
-> AiContextWindowService 按消息数和字符预算裁剪上下文窗口
-> determineAgentRoute 判断 SHOP_GUIDE / REVIEW_RAG / COUPON / ORDER_GUARD / CHAT
-> AgentMemorySelectionPolicy 读取相关长期记忆
-> 对推荐请求预先调用 recommendShops 生成候选
-> ChatClient + SpotAiChatTools 生成回答
-> MySQL 保存完整 user/assistant 历史
-> ChatMemory 写入当前轮 user/assistant 短期记忆
-> PreferenceExtractorAgent 抽取长期偏好
-> 旧历史溢出时写入 conversation.summary.default
```

## 5. 记忆写入规则

长期偏好：

- 未登录用户不写长期记忆。
- 只保存明确、稳定的偏好，例如预算、口味、商圈、忌口。
- 低置信度候选不写入。
- 相同 key、相同 JSON、置信度没有提升时跳过写入。
- 相同内容但置信度提升时允许更新。
- 内容变化时允许更新。

历史摘要：

- 当完整历史超过上下文窗口的消息数或字符预算时，将旧消息交给 `ConversationSummaryAgent`。
- 摘要写入 `conversation.summary.default`。
- 摘要内容不变且置信度没有提升时跳过写入。
- 当 LLM 摘要失败时，使用规则摘要兜底。

上下文窗口：

- `ChatMemory` 只负责当前会话的短期消息窗口，不作为完整历史库。
- `AiContextWindowService` 先使用 Spring AI `MessageWindowChatMemory` 保留最近消息，再按 `context-window-max-chars` 从新到旧压缩进入 prompt 的历史。
- 如果最新一条历史消息本身超过字符预算，会截断该消息，避免空窗口。
- 长期记忆进入 prompt 前先按 Agent 路由筛选 namespace，再按 `memory-max-chars` 控制单条记忆长度，按 `memory-total-max-chars` 控制本轮可见长期记忆总长度。
- 完整历史仍保存在 MySQL，用于前端展示、审计和溢出摘要。

## 6. 推荐链路

推荐请求会综合三类信息：

1. 用户当前问题，例如“推荐几家人均 50 左右的火锅店”。
2. 长期偏好，例如 `dining.preference.budget`、`dining.preference.taste`、`dining.preference.area`。
3. 历史摘要，例如 `conversation.summary.default` 中的预算、口味、区域线索。

`RecommendationPreferenceResolver` 会解析出：

```text
minPrice / maxPrice
keyword
area
limit
```

然后调用：

```java
spotAiChatTools.recommendShops(minPrice, maxPrice, keyword, area, 5)
```

工具返回的店铺包含 `spotai://shop/{id}` 链接，前端 AI 聊天窗口可以跳转到店铺详情。

## 7. 前端用户控制

前端“我的”页已支持：

- 查看 AI 推荐偏好。
- 查看 AI 对话摘要。
- 查看 AI 最近对话。
- 删除单条 AI 偏好/摘要。
- 一键清除全部 AI 长期记忆：`DELETE /ai/memories`。
- 清空 AI 对话历史：`DELETE /ai/conversations`。

AI 聊天窗口已支持：

- 未登录用户：聊天历史只保存在前端本地。
- 登录用户：聊天历史保存到后端数据库。
- 后端返回 `memoryUpdated=true` 时，前端刷新 AI 记忆。
- 成功对话后，前端刷新最近 AI 对话预览。

## 8. 当前 API

| API | 说明 | 登录 |
|---|---|---|
| `POST /ai/chat` | AI 对话入口 | 可匿名 |
| `GET /ai/conversations/recent?limit=20` | 最近 AI 对话 | 需要 |
| `DELETE /ai/conversations` | 清空当前用户 AI 对话历史 | 需要 |
| `GET /ai/memories` | 查询当前用户 AI 记忆 | 需要 |
| `DELETE /ai/memories/{memoryKey}` | 删除单条 AI 记忆 | 需要 |
| `DELETE /ai/memories` | 清空当前用户全部 AI 记忆 | 需要 |

Tool 观测：

- `POST /ai/chat` 返回 `usedTools`。
- 当前 `usedTools` 记录后端确定性预筛选的工具使用，例如 `recommendShops`。
- AI 聊天窗口在 `usedTools` 包含 `recommendShops` 时显示“已查询推荐候选”提示。
- 未登录用户的前端本地聊天历史会保留 `usedTools`。
- 登录用户的 assistant 历史消息会在 `tb_ai_conversation_*`.`metadata` 中持久化 `usedTools`，需要执行 `sql/migrate_ai_conversation_metadata.sql`。
- 旧数据没有 `metadata` 时，前端仍会在历史 assistant 文本包含 `spotai://shop/{id}` 链接时恢复推荐工具提示。
- 登录用户调用 `searchShop`、`queryShopDetail`、`recommendShops`、`queryReviewSummary`、`queryCoupons` 时，会写入 `tb_ai_tool_call_log_*`，记录工具名、风险等级、输入输出、目标对象和状态。
- 匿名用户不会写工具调用日志。

## 9. 当前边界

- 目前是单 `ChatClient + tools` 的第一版 Agent 机制，还不是完整 Graph 编排。
- 高风险动作仍不应由模型直接执行，例如下单、退款、取消订单。
- 低风险 Tool 调用日志已落地；中高风险确认卡片和 confirm token 校验仍是后续阶段。

已新增 `claimCoupon` 作为中风险工具。流程：

1. AI 调用 `claimCoupon(voucherId)`。
2. 后端检测到 `medium` 风险等级，不执行操作，生成 `confirmToken`，写入 `tb_ai_tool_call_log_*` 状态为 `pending`。
3. 后端返回 `{"status":"CONFIRM_REQUIRED","toolName":"claimCoupon","confirmToken":"xxx"}`。
4. 前端展示确认卡片。
5. 用户点击确认 → `POST /ai/tool/confirm`。
6. 后端查找日志 → 校验用户 → 执行 `voucherService.claimVoucher(voucherId)` → 更新日志状态为 `confirmed`。
7. 返回操作结果。

- Spring AI Alibaba 的长期 MemoryStore 暂未直接替换 MySQL 实现；当前使用 `UserMemoryStore` 保持 namespace/key 语义，后续可以替换底层存储。
- Redis/JDBC ChatMemory 可作为后续短期记忆持久化方向；当前代码已通过接口注入留出替换点。

## 10. 已覆盖测试

主要测试类：

- `ToolConfirmServiceTest`
- `SpringAiChatServiceTest`
- `AiShortTermMemoryServiceTest`
- `AiContextWindowServiceTest`
- `SpringAiPreferenceExtractorAgentTest`
- `SpringAiConversationSummaryAgentTest`
- `RecommendationPreferenceResolverTest`
- `AgentMemorySelectionPolicyTest`
- `MysqlUserMemoryStoreTest`
- `SpotAiChatToolsTest`
- `AiChatControllerTest`
- `AiChatControllerAuthTest`

常用验证命令：

```powershell
mvn -q "-Dtest=AiShortTermMemoryServiceTest,AiContextWindowServiceTest,AiChatControllerAuthTest,AiChatControllerTest,SpringAiChatServiceTest,SpringAiConversationSummaryAgentTest,SpringAiPreferenceExtractorAgentTest,RecommendationPreferenceResolverTest,AgentMemorySelectionPolicyTest,UserMemoryKeyTest,MysqlUserMemoryStoreTest,SpotAiChatToolsTest" test
```

```powershell
mvn -q test
```
