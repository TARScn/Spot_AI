# Spot AI AI Agent 与记忆管理说明

> 核对日期：2026-07-14  
> 核对范围：`SpringAiChatService`、`SpotAiChatTools`、AI 相关 Repository、`AiChatController`、前端 AI 助手。

## 1. 当前实现结论

当前项目没有引入 Spring AI Alibaba Graph 的 `RoutingAgent` / `SequentialAgent` 编排，也没有直接使用 Spring AI Alibaba 的长期 `MemoryStore` 组件。实际实现采用的是：

- `SpringAiChatService` 作为 AI 对话协调器。
- `ChatClient.builder(chatModel).defaultTools(spotAiChatTools).build()` 注册工具。
- DeepSeek 作为 OpenAI 兼容聊天模型。
- DashScope 作为向量化模型。
- MySQL 自建表保存长期记忆和对话历史。
- `AiContextWindowService` 控制上下文窗口，避免无限历史进入模型。
- 前端 `AiChatWidget` 展示 Markdown、店铺链接和工具确认卡片。

这套方案已经覆盖当前核心场景：找店推荐、查询店铺详情、评价总结、优惠查询、优惠券确认领取、用户偏好记忆和历史展示。

## 2. Agent 分层

| 层级 | 当前类 | 作用 |
| --- | --- | --- |
| 对话协调 | `SpringAiChatService` | 构建 prompt、上下文、记忆、业务上下文并调用模型 |
| 工具执行 | `SpotAiChatTools` | 暴露 `@Tool` 方法，由模型决定是否调用 |
| 偏好提取 | `SpringAiPreferenceExtractorAgent` | 从用户消息中提取预算、口味、商圈、场景等偏好 |
| 历史摘要 | `SpringAiConversationSummaryAgent` | 对历史对话做摘要，减少上下文长度 |
| 短期记忆 | `AiShortTermMemoryService` | 按用户取最近对话 |
| 长期记忆 | `UserMemoryStore` / `MysqlUserMemoryStore` | 用 namespace/key 语义保存用户偏好 |
| 工具审计 | `AiToolCallLogService` | 记录工具名、风险等级、输入输出、确认状态 |

## 3. 可用工具

| 工具 | 风险等级 | 能力 |
| --- | --- | --- |
| `searchShop` | low | 按关键词检索商家，返回可点击 `spotai://shop/{id}` 链接 |
| `queryShopDetail` | low | 查询店铺名称、评分、人均、地址、营业时间等 |
| `recommendShops` | low | 按预算、关键词、商圈、评分、距离和评价摘要推荐店铺 |
| `queryReviewSummary` | low | 查询店铺评论 AI 摘要 |
| `queryCoupons` | low | 查询店铺可用优惠券 |
| `claimCoupon` | medium | 领取普通代金券，需要前端确认卡片后执行 |

当前没有让 AI 直接执行高风险秒杀下单或取消订单。

## 4. 对话流程

1. 前端调用 `POST /ai/chat`，可携带 `message`、`shopId`、`history`。
2. 后端识别用户身份：游客历史只由前端保存；登录用户历史会写入 MySQL。
3. `SpringAiChatService` 读取最近对话、长期偏好、当前店铺上下文。
4. `AiContextWindowService` 按消息数和字符数裁剪上下文。
5. `ChatClient` 调用 DeepSeek，并允许模型调用 `SpotAiChatTools`。
6. 低风险工具直接执行；中风险工具返回确认请求。
7. 前端显示 AI 回复、店铺链接、工具确认卡片。
8. 登录用户的对话、工具调用和偏好更新写入数据库。

## 5. 记忆管理

### 5.1 对话历史

| 表 | 说明 |
| --- | --- |
| `tb_ai_conversation_0`、`tb_ai_conversation_1` | 保存登录用户的用户消息、AI 回复、店铺上下文和工具元数据 |

游客未登录时，历史只保存在前端 localStorage；登录后通过后端接口保存和读取。

### 5.2 长期偏好

| 表 | 说明 |
| --- | --- |
| `tb_ai_user_memory_0`、`tb_ai_user_memory_1` | 保存用户长期偏好，例如预算、口味、商圈、场景、优惠倾向 |

当前长期记忆使用自建 `UserMemoryStore` 接口，语义上接近 MemoryStore：`namespace + memoryKey + summary + metadata`。如果后续引入 Spring AI Alibaba MemoryStore，可以优先替换 `MysqlUserMemoryStore` 底层实现，而不改变业务层调用。

## 6. 上下文窗口

配置项位于 `application.yml`：

```yaml
spotai:
  ai:
    chat:
      context-window-max-messages: 12
      context-window-max-chars: 2400
      visible-history-max-messages: 20
      history-max-chars: 800
      memory-max-chars: 800
      memory-total-max-chars: 1600
```

设计原则：

- 最近对话优先。
- 当前店铺上下文优先。
- 长期偏好只放摘要，不放完整原始历史。
- 历史过长时使用摘要和裁剪，避免超出模型上下文。

## 7. 前端能力

- AI 悬浮助手在任意页面可打开。
- 只有在店铺详情页才显示当前店铺名。
- AI 回复支持 Markdown 渲染。
- `spotai://shop/{id}` 会在前端转换为可点击店铺详情链接。
- 中风险工具调用展示确认卡片，用户点击确认后调用 `POST /ai/tool/confirm`。
- “我的”页面可查看部分 AI 历史和长期记忆，并支持删除。

## 8. 接口

| 方法 | 路径 | 登录 | 说明 |
| --- | --- | --- | --- |
| `POST` | `/ai/chat` | 可匿名 | AI 对话入口 |
| `GET` | `/ai/conversations/recent` | 是 | 查询最近 AI 对话 |
| `DELETE` | `/ai/conversations` | 是 | 清空当前用户 AI 对话 |
| `GET` | `/ai/memories` | 是 | 查询当前用户 AI 记忆 |
| `DELETE` | `/ai/memories/{memoryKey}` | 是 | 删除单条 AI 记忆 |
| `DELETE` | `/ai/memories` | 是 | 清空 AI 记忆 |
| `POST` | `/ai/tool/confirm` | 是 | 确认或拒绝中风险工具调用 |

## 9. 当前边界

- 当前不是多 Agent Graph 编排，而是“单协调器 + 工具调用 + 记忆服务”模式。
- 长期记忆不是官方 MemoryStore 直接落地，而是项目自建 MySQL 实现。
- AI 推荐依赖当前数据库中的店铺、评价摘要和优惠券数据；数据不足时推荐质量会受影响。
- 工具确认当前覆盖普通代金券领取，秒杀下单仍走前端业务页面。

## 10. 相关测试

重点测试类：

- `SpotAiChatToolsTest`
- `SpringAiChatServiceTest`
- `SpringAiPreferenceExtractorAgentTest`
- `AiContextWindowServiceTest`
- `AiShortTermMemoryServiceTest`
- `MysqlUserMemoryStoreTest`
- `AiChatControllerTest`
- `AiChatControllerAuthTest`
