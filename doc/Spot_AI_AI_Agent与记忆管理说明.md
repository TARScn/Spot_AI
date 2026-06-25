# Spot AI AI Agent 与记忆管理说明

## 1. 设计结论

Spot AI 的 AI 小部件后续按“**单窗口入口、后端 Multi-agent 分工、用户级记忆**”设计。

前端仍然只有一个 AI 对话窗口，不暴露 `sessionId` 和 Agent 细节；后端根据登录用户 ID 生成内部 `threadId`，由主控 Agent 负责路由，多个专业 Agent 各自处理有限任务。

推荐第一版 Agent 拆分：

| Agent | 职责 | 是否直接面向用户 |
|---|---|---|
| `SpotCoordinatorAgent` | 判断意图、选择子 Agent、汇总最终回复 | 是 |
| `PreferenceExtractorAgent` | 从用户话语中抽取长期偏好、禁忌、预算、区域 | 否 |
| `ShopGuideAgent` | 找店、解释店铺信息、结合用户偏好推荐 | 否 |
| `ReviewRagAgent` | 调用评论 RAG，总结店铺评价优缺点 | 否 |
| `CouponAgent` | 查询优惠券、解释优惠规则 | 否 |
| `OrderGuardAgent` | 处理秒杀、下单、取消等高风险动作的确认流程 | 否 |

第一版建议采用 **Supervisor / Agent Tool 模式**：主控 Agent 将子 Agent 当作工具调用，子 Agent 不直接和用户连续对话。这样更适合 Spot AI 当前“一个 AI 小窗口”的产品形态，也方便统一做权限、审计和风险确认。

## 2. 官方文档依据

本设计参考以下官方文档：

- Spring AI Alibaba README: https://github.com/alibaba/spring-ai-alibaba
- Spring AI Alibaba ReactAgent 快速开始: https://java2ai.com/docs/quick-start/
- Spring AI Alibaba Multi-agent: https://java2ai.com/docs/frameworks/agent-framework/advanced/multi-agent/
- Spring AI Alibaba Agent Tool: https://java2ai.com/docs/frameworks/agent-framework/advanced/agent-tool/
- Spring AI Alibaba Memory: https://java2ai.com/docs/frameworks/agent-framework/advanced/memory/
- Spring AI Alibaba RAG: https://java2ai.com/docs/frameworks/agent-framework/advanced/rag/
- Spring AI Chat Memory: https://docs.spring.io/spring-ai/reference/api/chat-memory.html

关键依据：

1. Spring AI Alibaba 定位为可构建 Agentic、Workflow、Multi-agent 应用的框架，并提供 `SequentialAgent`、`ParallelAgent`、`RoutingAgent`、`LoopAgent` 等编排模式。
2. 官方 Multi-agent 文档指出，当单个 Agent 工具太多、上下文或记忆过大、任务需要专业化时，适合拆分为多个专业 Agent。
3. Multi-agent 支持 Tool Calling / Supervisor 与 Handoffs 两种模式；Spot AI 第一版更适合集中控制的 Tool Calling 模式。
4. 官方文档强调 Multi-agent 的核心是上下文工程：决定每个 Agent 看到什么信息、是否包含中间推理、输入输出格式如何约束。
5. Spring AI Alibaba Memory 将短期记忆和长期记忆分开，长期记忆以 `namespace` / `key` 组织 JSON 数据。
6. Spring AI 文档区分 `Chat Memory` 与完整 `Chat History`：Memory 服务于当前模型调用，完整历史应另存数据库。

## 3. 总体流程

```text
用户输入
-> SpotCoordinatorAgent 判断意图
-> 读取必要短期上下文和长期偏好
-> 按需调用专业子 Agent
   -> PreferenceExtractorAgent 抽取偏好
   -> ShopGuideAgent 查询商户
   -> ReviewRagAgent 查询评论 RAG
   -> CouponAgent 查询优惠券
   -> OrderGuardAgent 生成高风险确认
-> 主控 Agent 汇总回复
-> 保存聊天历史、工具调用日志、可确认的长期偏好
```

关键原则：

- 一个用户默认只有一个 AI 窗口：`threadId = "user:" + userId + ":default"`。
- 前端只保存临时 UI 状态，不管理 Agent、`threadId` 或记忆。
- 子 Agent 只拿完成任务需要的上下文，不共享完整聊天历史。
- 长期记忆只保存稳定事实，不保存一次性问题。
- 高风险动作必须走确认卡片，不能由模型直接执行。

## 4. PreferenceExtractorAgent

`PreferenceExtractorAgent` 是第一版最值得单独拆出的 Agent，专门负责从对话中抽取用户偏好。

输入：

```json
{
  "userId": 10001,
  "latestUserMessage": "我不吃辣，想找安静一点、人均 80 左右的店",
  "recentMessages": [],
  "existingMemories": []
}
```

输出：

```json
{
  "memories": [
    {
      "memoryKey": "dining.preference.taste",
      "memoryType": "preference",
      "value": {
        "dislikes": ["辣"]
      },
      "confidence": 0.95,
      "action": "UPSERT"
    },
    {
      "memoryKey": "dining.preference.environment",
      "memoryType": "preference",
      "value": {
        "likes": ["安静"]
      },
      "confidence": 0.9,
      "action": "UPSERT"
    },
    {
      "memoryKey": "dining.preference.budget",
      "memoryType": "preference",
      "value": {
        "budgetPerPerson": 80
      },
      "confidence": 0.85,
      "action": "UPSERT"
    }
  ]
}
```

保存规则：

- 明确表达的长期偏好才保存，例如“不吃辣”“人均 80 左右”“喜欢安静”。
- 一次性任务不保存，例如“今天想吃火锅”“帮我看看这家店”。
- 低于 `0.7` 的偏好默认不写长期记忆。
- 每条记忆保留 `confidence`、`sourceMessageId`、`updatedAt`，方便后续覆盖和审计。
- 用户显式否定旧偏好时，写入 `DELETE` 或更新为新值。

## 5. 记忆与历史存储

### 5.1 聊天历史表

完整聊天历史用于页面恢复、审计和后续摘要，不直接整段进入 prompt。

```sql
create table tb_ai_chat_message (
  id bigint not null primary key,
  user_id bigint not null,
  role varchar(20) not null,
  content text not null,
  route varchar(32) not null default 'CHAT',
  agent_name varchar(64) null,
  tool_call_id varchar(64) null,
  token_count int null,
  truncated tinyint not null default 0,
  status tinyint not null default 1,
  create_time timestamp not null default current_timestamp,
  key idx_user_time (user_id, create_time),
  key idx_agent_name (agent_name)
);
```

### 5.2 用户长期记忆表

```sql
create table tb_ai_user_memory (
  id bigint not null primary key,
  user_id bigint not null,
  memory_key varchar(128) not null,
  memory_type varchar(32) not null,
  memory_json json not null,
  confidence decimal(5, 4) not null default 0.8000,
  source_message_id bigint null,
  source_agent varchar(64) not null default 'PreferenceExtractorAgent',
  status tinyint not null default 1,
  create_time timestamp not null default current_timestamp,
  update_time timestamp not null default current_timestamp on update current_timestamp,
  unique key uk_user_memory_key (user_id, memory_key),
  key idx_user_type (user_id, memory_type)
);
```

推荐 namespace / key：

```text
["spotai", "user", "{userId}", "preference"]
  dining.preference.taste
  dining.preference.environment
  dining.preference.budget
  dining.preference.area

["spotai", "user", "{userId}", "avoid"]
  dining.avoid.keyword
```

### 5.3 工具调用日志表

所有子 Agent 调用业务工具都要记录。

```sql
create table tb_ai_tool_call_log (
  id bigint not null primary key,
  user_id bigint not null,
  agent_name varchar(64) not null,
  tool_name varchar(100) not null,
  risk_level varchar(20) not null,
  tool_input json null,
  tool_output json null,
  status varchar(20) not null,
  create_time timestamp not null default current_timestamp,
  key idx_user_time (user_id, create_time),
  key idx_agent_tool (agent_name, tool_name)
);
```

## 6. Agent 上下文分层

| 层级 | 内容 | 使用方式 |
|---|---|---|
| 系统规则 | 安全边界、工具调用规则 | 每次进入主控 Agent |
| 用户输入 | 当前问题 | 每次进入主控 Agent |
| 短期记忆 | 最近 10-20 条对话 | `MemorySaver` / `ChatMemory` |
| 长期偏好 | 口味、预算、区域、禁忌 | 检索后注入相关部分 |
| 业务上下文 | 店铺、优惠券、评论 RAG、订单状态 | 由对应子 Agent 或 tool 获取 |
| 工具观察 | 本轮工具结果 | 只在本轮和必要后续步骤中使用 |
| 完整历史 | 全量聊天记录 | MySQL 归档，不直接进入 prompt |

子 Agent 默认不接收完整 `messages`。需要跨 Agent 传递信息时，使用结构化 `outputKey`，例如：

```text
PreferenceExtractorAgent -> preferenceExtractionResult
ReviewRagAgent -> reviewSummaryResult
CouponAgent -> couponQueryResult
```

## 7. 后端模块划分

```text
com.tars.spotai.ai
  controller
    AiChatController
  agent
    SpotCoordinatorAgent
    PreferenceExtractorAgent
    ShopGuideAgent
    ReviewRagAgent
    CouponAgent
    OrderGuardAgent
  memory
    AiChatMemoryService
    AiUserMemoryService
    AiPreferenceMemoryRepository
  tool
    ShopTools
    ReviewTools
    CouponTools
    OrderTools
  dto
    AiChatRequestDTO
    AiChatResponseDTO
    PreferenceExtractionResult
  repository
    AiChatMessageRepository
    AiUserMemoryRepository
    AiToolCallLogRepository
```

第一版可以先不直接上复杂 Graph 编排，先用 `SpotCoordinatorAgent` 手写路由，保持可控；当工具和子 Agent 增多后，再迁移到 Spring AI Alibaba 的 `LlmRoutingAgent`、`SequentialAgent` 或 Agent Tool 模式。

## 8. 实现顺序建议

1. 新增 AI 相关包结构和基础 DTO。
2. 保存登录用户聊天历史到 `tb_ai_chat_message`。
3. 新增 `PreferenceExtractorAgent`，只抽取明确偏好。
4. 新增 `tb_ai_user_memory`，保存结构化偏好。
5. 主控 Agent 回答前读取相关偏好，注入 prompt。
6. 将评论 RAG 总结封装为 `ReviewRagAgent`。
7. 将商户、优惠券查询封装为低风险 tools。
8. 为领券、秒杀、取消订单增加确认卡片和工具日志。
9. 需要更复杂编排时，再接入 Spring AI Alibaba Multi-agent / Graph。

## 9. 当前版本边界

- 前端仍只有一个 AI 小窗口。
- 第一版不让用户切换 Agent。
- 未登录用户不写长期记忆。
- Agent 不保存敏感信息，例如密码、身份证、手机号完整值。
- 高风险工具只生成确认，不直接执行。
- Multi-agent 是后端实现细节，不增加前端复杂度。
