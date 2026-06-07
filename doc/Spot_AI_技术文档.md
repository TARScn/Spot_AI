# Spot_AI_技术文档

## 1. 项目目标

基于黑马点评业务进行扩展，构建一个面向本地生活场景的微服务系统，并加入 AI Agent 能力，实现：

- 商户搜索与推荐
- 优惠券秒杀
- 用户点评与评论分析
- 用户画像与个性化推荐
- AI 自然语言找店
- AI 评论总结与避坑分析
- AI 优惠券推荐
- AI 工具调用与下单确认

项目重点不是简单复刻黑马点评，而是在原业务基础上补齐企业级后端能力和 AI 应用能力。

---

## 2. 技术栈

## 2.1 后端技术栈

| 类型 | 技术 |
|---|---|
| 编程语言 | Java 17 |
| 微服务框架 | Spring Boot、Spring Cloud Alibaba |
| 注册中心 | Nacos |
| 配置中心 | Nacos Config |
| 网关 | Spring Cloud Gateway |
| RPC 调用 | OpenFeign |
| ORM | MyBatis Plus |
| 数据库 | MySQL |
| 缓存 | Redis |
| 消息队列 | RabbitMQ / RocketMQ |
| 搜索引擎 | Elasticsearch |
| 分布式事务 | Seata，可选 |
| 限流熔断 | Sentinel |
| 认证鉴权 | Sa-Token / JWT |
| 接口文档 | Knife4j |
| 对象存储 | MinIO |
| 部署 | Docker、Docker Compose |
| 压测 | JMeter |

## 2.2 AI 技术栈

| 类型 | 技术 |
|---|---|
| Java AI 框架 | Spring AI / LangChain4j |
| 大模型 | DeepSeek / Qwen / OpenAI 兼容模型 |
| Embedding | bge-small-zh / bge-large-zh |
| 向量数据库 | PostgreSQL + pgvector / Milvus / Elasticsearch Vector |
| Agent 能力 | Tool Calling / Function Calling |
| RAG | 评论知识库、商户知识库、规则知识库 |
| 对话记忆 | Redis + MySQL |

---

## 3. 微服务拆分

## 3.1 服务列表

```text
dianping-gateway          网关服务
dianping-auth             登录认证服务
dianping-user             用户服务
dianping-shop             商户服务
dianping-review           评论服务
dianping-coupon           优惠券服务
dianping-order            订单服务
dianping-search           搜索服务
dianping-feed             Feed 流服务
dianping-ai-agent         AI Agent 服务
dianping-common           公共模块
```

## 3.2 服务职责

| 服务 | 职责 |
|---|---|
| gateway | 路由转发、鉴权、限流、跨域 |
| auth | 登录、验证码、Token 生成、Token 校验 |
| user | 用户信息、关注、粉丝、用户画像 |
| shop | 商户、分类、附近商户、商户缓存 |
| review | 点评、评论、点赞、评论统计 |
| coupon | 优惠券、秒杀券、库存预扣 |
| order | 订单创建、订单状态、订单取消 |
| search | ES 商户搜索、关键词搜索、条件筛选 |
| feed | 探店笔记、关注流、推模式 Feed |
| ai-agent | 自然语言理解、工具调用、RAG、推荐解释 |
| common | 通用返回、异常、工具类、DTO、枚举 |

---

## 4. 核心业务功能

## 4.1 用户模块

### 功能

- 手机号验证码登录
- Token 鉴权
- 用户信息查询
- 用户资料修改
- 关注用户
- 取消关注
- 查询关注列表
- 查询粉丝列表
- 用户签到
- 用户画像维护
- 用户浏览历史记录
- 用户收藏记录

### 实现步骤

1. 用户输入手机号，请求验证码。
2. 验证码存入 Redis，设置过期时间。
3. 用户登录时校验验证码。
4. 登录成功后生成 JWT / Sa-Token。
5. 用户信息写入 Redis，减少频繁查库。
6. 网关统一校验 Token。
7. 关注关系存入 MySQL。
8. 共同关注使用 Redis Set 计算交集。
9. 签到功能使用 Redis BitMap。
10. 浏览、收藏、下单行为写入用户画像表。

### 核心表

```sql
user
user_profile
user_follow
user_preference
user_behavior_log
```

---

## 4.2 商户模块

### 功能

- 商户分类查询
- 商户列表查询
- 商户详情查询
- 商户新增、修改、下架
- 根据分类查询商户
- 根据地理位置查询附近商户
- 商户缓存
- 热门商户缓存预热
- 商户评分统计
- 商户标签维护

### 实现步骤

1. 商户基础数据存入 MySQL。
2. 商户分类数据缓存到 Redis。
3. 商户详情采用 Cache Aside 模式。
4. 查询商户时先查 Redis，未命中再查 MySQL。
5. 对不存在商户缓存空对象，防止缓存穿透。
6. 热点商户使用逻辑过期解决缓存击穿。
7. 更新商户信息时先更新数据库，再删除缓存。
8. 商户经纬度写入 Redis GEO。
9. 附近商户查询使用 Redis GEOSEARCH。
10. 商户变更后发送 MQ 消息同步到 Elasticsearch。

### 核心表

```sql
shop
shop_type
shop_tag
shop_score
```

---

## 4.3 搜索模块

### 功能

- 关键词搜索商户
- 分类筛选
- 区域筛选
- 价格区间筛选
- 评分排序
- 距离排序
- 综合排序
- 搜索词自动补全
- 热门搜索词
- 搜索日志记录

### 实现步骤

1. 商户数据从 MySQL 同步到 Elasticsearch。
2. 新增、修改、下架商户时发送 MQ 消息。
3. search 服务消费消息并更新 ES 索引。
4. 使用 IK 分词器支持中文搜索。
5. 根据关键词、分类、区域、价格构建 ES 查询。
6. 根据评分、销量、距离计算综合排序。
7. 用户搜索日志写入 MySQL 或 MQ。
8. 热门搜索词使用 Redis Sorted Set 统计。
9. 自动补全使用 ES Completion Suggester。
10. AI Agent 找店时优先调用 search 服务。

### ES 索引字段

```json
{
  "shopId": "long",
  "name": "text",
  "category": "keyword",
  "area": "keyword",
  "address": "text",
  "avgPrice": "integer",
  "score": "double",
  "location": "geo_point",
  "tags": "keyword",
  "description": "text",
  "status": "integer"
}
```

---

## 4.4 评论模块

### 功能

- 发布评论
- 查询商户评论
- 评论点赞
- 评论取消点赞
- 评论分页
- 评论热度排序
- 评论图片上传
- 评论情绪分析
- 评论摘要生成
- 商户优缺点分析

### 实现步骤

1. 用户发布评论，写入 review 表。
2. 评论图片上传到 MinIO。
3. 评论发布后发送 MQ 消息。
4. 异步更新商户评分。
5. 异步生成评论向量，写入向量库。
6. 评论点赞数据使用 Redis Set 记录。
7. 评论点赞数异步落库。
8. 查询评论时优先查 MySQL 分页。
9. AI 总结评论时调用 RAG 检索相关评论。
10. 评论情绪分析结果写入 review_ai_analysis 表。

### 核心表

```sql
review
review_image
review_like
review_ai_analysis
review_embedding
```

---

## 4.5 优惠券模块

### 功能

- 商户发布优惠券
- 查询商户优惠券
- 用户领取优惠券
- 秒杀券管理
- 秒杀库存预扣
- 一人一单校验
- 秒杀资格校验
- 优惠券过期处理

### 实现步骤

1. 优惠券基础信息写入 MySQL。
2. 秒杀开始前将券库存加载到 Redis。
3. 用户请求秒杀时执行 Lua 脚本。
4. Lua 中同时校验库存和一人一单。
5. 校验成功后 Redis 预扣库存。
6. 发送订单创建消息到 MQ。
7. order 服务异步消费消息创建订单。
8. MySQL 使用唯一索引兜底防止重复下单。
9. 如果订单创建失败，发送补偿消息恢复库存。
10. 定时任务处理过期优惠券。

### Redis Key 设计

```text
seckill:stock:{voucherId}
seckill:order:{voucherId}
coupon:user:{userId}
```

### Lua 脚本逻辑

```text
1. 判断库存是否大于 0
2. 判断用户是否已购买
3. 扣减库存
4. 记录用户已购买
5. 返回成功
```

---

## 4.6 订单模块

### 功能

- 创建普通订单
- 创建秒杀订单
- 查询订单详情
- 查询用户订单列表
- 取消订单
- 支付状态更新，模拟
- 订单超时关闭
- 订单状态流转

### 实现步骤

1. 普通订单直接写入 MySQL。
2. 秒杀订单通过 MQ 异步创建。
3. order 服务消费 seckill order 消息。
4. 创建订单前再次校验用户和优惠券状态。
5. 使用数据库唯一索引防止重复订单。
6. 订单创建成功后更新订单状态。
7. 订单未支付时写入延迟队列。
8. 延迟队列触发后检查订单状态。
9. 未支付订单自动关闭。
10. 关闭订单时恢复优惠券库存。

### 订单状态

```text
待支付
已支付
已取消
已完成
已退款
已超时关闭
```

---

## 4.7 Feed 流模块

### 功能

- 发布探店笔记
- 查询探店笔记
- 点赞笔记
- 取消点赞
- 关注用户动态流
- 滚动分页查询
- 热门笔记推荐
- AI 生成笔记标题
- AI 生成笔记摘要

### 实现步骤

1. 用户发布探店笔记，写入 blog 表。
2. 图片上传到 MinIO。
3. 发布后查询粉丝列表。
4. 使用推模式将笔记 ID 写入粉丝收件箱。
5. 收件箱使用 Redis Sorted Set。
6. 查询关注流时使用滚动分页。
7. 点赞数据使用 Redis Sorted Set。
8. 点赞排行榜使用 Redis ZSet。
9. AI 根据正文生成标题和摘要。
10. 热门笔记根据点赞、评论、发布时间计算热度。

### Redis Key 设计

```text
feed:user:{userId}
blog:liked:{blogId}
blog:hot
```

---

## 5. AI Agent 功能

## 5.1 自然语言找店

### 功能说明

用户输入自然语言，系统自动解析需求并推荐商户。

### 示例

```text
帮我找深圳南山区适合情侣约会、人均 200 以下的西餐厅。
```

### 实现步骤

1. ai-agent 服务接收用户输入。
2. 使用大模型提取结构化参数。
3. 参数包括城市、区域、分类、预算、场景、距离等。
4. 调用 search 服务查询候选商户。
5. 调用 review 服务获取评论摘要。
6. 调用 coupon 服务获取优惠券。
7. 计算综合推荐分。
8. 大模型生成推荐理由。
9. 返回推荐列表。

### 工具定义

```text
searchShop(city, area, category, budgetMin, budgetMax, scene, keyword)
queryShopReviewSummary(shopId)
queryShopCoupons(shopId)
```

---

## 5.2 评论 RAG 总结

### 功能说明

针对用户的问题，从评论知识库中检索相关评论，再生成总结。

### 示例问题

```text
这家店适合约会吗？
这家店有什么缺点？
这家店排队严重吗？
```

### 实现步骤

1. 评论发布后发送 MQ 消息。
2. ai-agent 或 review 服务消费评论消息。
3. 清洗评论文本。
4. 对评论内容进行 Chunk 切分。
5. 调用 Embedding 模型生成向量。
6. 向量写入 pgvector / Milvus。
7. 用户提问时生成问题向量。
8. 从向量库召回 TopK 评论片段。
9. 将召回片段和用户问题拼接成 Prompt。
10. 大模型生成总结结果。

### RAG 数据

```text
review_id
shop_id
content
score
sentiment
scene_tags
embedding
```

---

## 5.3 智能优惠券推荐

### 功能说明

根据用户需求、商户信息、用户画像和优惠券状态推荐最合适的优惠券。

### 示例

```text
我想吃日料，有没有比较划算的券？
```

### 实现步骤

1. AI 解析用户需求。
2. 调用 user 服务获取用户画像。
3. 调用 search 服务查询相关商户。
4. 调用 coupon 服务查询可用优惠券。
5. 过滤不可用、已过期、库存不足优惠券。
6. 根据优惠力度、用户偏好、距离、评分排序。
7. 大模型生成推荐理由。
8. 返回优惠券列表。

---

## 5.4 AI 秒杀助手

### 功能说明

用户可以通过自然语言发起秒杀请求，但高风险操作必须二次确认。

### 示例

```text
帮我抢这张 99 元双人餐券。
```

### 实现步骤

1. AI 识别秒杀意图。
2. 查询优惠券详情。
3. 查询用户登录状态。
4. 查询库存。
5. 查询用户是否已购买。
6. 返回确认卡片。
7. 用户确认后调用 createSeckillOrder 工具。
8. coupon 服务执行 Redis Lua 预扣库存。
9. order 服务异步创建订单。
10. 返回订单结果。

### 安全要求

```text
AI 不能直接创建订单。
AI 不能跳过确认。
AI 不能代替用户支付。
AI 不能操作其他用户订单。
```

---

## 5.5 用户画像与个性化推荐

### 功能说明

根据用户行为生成画像，用于商户和优惠券推荐。

### 用户行为

```text
浏览商户
收藏商户
点赞评论
发布评论
领取优惠券
购买订单
搜索关键词
对 AI 推荐结果的点击
```

### 实现步骤

1. 各业务服务产生用户行为事件。
2. 事件发送到 MQ。
3. user 服务消费行为事件。
4. 更新用户画像标签。
5. 推荐时读取用户画像。
6. 根据画像调整排序权重。
7. AI 生成个性化推荐解释。

### 推荐排序

```text
final_score =
  shop_score * 0.30
+ distance_score * 0.20
+ budget_match_score * 0.20
+ preference_score * 0.20
+ coupon_score * 0.10
```

---

## 6. AI 工具调用安全设计

## 6.1 工具风险等级

| 工具 | 风险等级 | 是否需要确认 |
|---|---|---|
| searchShop | 低 | 否 |
| queryShopDetail | 低 | 否 |
| queryCoupons | 低 | 否 |
| summarizeReviews | 低 | 否 |
| updateUserPreference | 中 | 是 |
| receiveCoupon | 中 | 是 |
| createSeckillOrder | 高 | 是 |
| cancelOrder | 高 | 是 |

## 6.2 实现步骤

1. 所有工具注册到工具白名单。
2. 每个工具配置风险等级。
3. 低风险工具可直接调用。
4. 中高风险工具先生成确认信息。
5. 用户确认后生成 confirmToken。
6. 后端校验 confirmToken。
7. 校验通过后执行真实业务操作。
8. 工具调用记录写入日志表。
9. 对工具调用进行限流。
10. 拦截 Prompt 注入指令。

## 6.3 工具调用日志表

```sql
CREATE TABLE ai_tool_call_log (
    id BIGINT PRIMARY KEY,
    user_id BIGINT,
    session_id VARCHAR(64),
    tool_name VARCHAR(100),
    risk_level VARCHAR(20),
    tool_input TEXT,
    tool_output TEXT,
    status VARCHAR(20),
    create_time DATETIME
);
```

---

## 7. 核心消息队列设计

## 7.1 消息主题

| Topic / Queue | 生产者 | 消费者 | 用途 |
|---|---|---|---|
| shop.update | shop | search | 同步 ES 商户索引 |
| review.created | review | ai-agent | 生成评论向量 |
| order.seckill.create | coupon | order | 异步创建秒杀订单 |
| order.timeout | order | order | 订单超时关闭 |
| user.behavior | 各业务服务 | user | 更新用户画像 |
| coupon.stock.rollback | order | coupon | 下单失败恢复库存 |

## 7.2 使用场景

### 商户索引同步

```text
shop 服务更新商户
  ↓
发送 shop.update 消息
  ↓
search 服务消费消息
  ↓
更新 Elasticsearch
```

### 评论向量化

```text
review 服务创建评论
  ↓
发送 review.created 消息
  ↓
ai-agent 服务消费消息
  ↓
生成 Embedding
  ↓
写入向量库
```

### 秒杀下单

```text
coupon 服务 Redis 预扣库存成功
  ↓
发送 order.seckill.create 消息
  ↓
order 服务创建订单
  ↓
失败则发送 coupon.stock.rollback
```

---

## 8. 数据库核心表

## 8.1 用户画像表

```sql
CREATE TABLE user_preference (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    preferred_categories VARCHAR(255),
    preferred_budget_min INT,
    preferred_budget_max INT,
    preferred_areas VARCHAR(255),
    preferred_scenes VARCHAR(255),
    avoid_keywords VARCHAR(255),
    create_time DATETIME,
    update_time DATETIME
);
```

## 8.2 用户行为表

```sql
CREATE TABLE user_behavior_log (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    behavior_type VARCHAR(50),
    target_type VARCHAR(50),
    target_id BIGINT,
    extra_info JSON,
    create_time DATETIME
);
```

## 8.3 评论 AI 分析表

```sql
CREATE TABLE review_ai_analysis (
    id BIGINT PRIMARY KEY,
    review_id BIGINT NOT NULL,
    shop_id BIGINT NOT NULL,
    sentiment VARCHAR(32),
    scene_tags VARCHAR(255),
    summary VARCHAR(500),
    create_time DATETIME
);
```

## 8.4 AI 会话表

```sql
CREATE TABLE ai_conversation (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    create_time DATETIME
);
```

## 8.5 AI 工具调用日志表

```sql
CREATE TABLE ai_tool_call_log (
    id BIGINT PRIMARY KEY,
    user_id BIGINT,
    session_id VARCHAR(64),
    tool_name VARCHAR(100),
    risk_level VARCHAR(20),
    tool_input TEXT,
    tool_output TEXT,
    status VARCHAR(20),
    create_time DATETIME
);
```

---

## 9. 核心接口设计

## 9.1 AI 对话接口

```http
POST /ai/chat
```

请求：

```json
{
  "sessionId": "s_10001",
  "message": "帮我找一家适合约会的人均 200 以下餐厅"
}
```

响应：

```json
{
  "type": "SHOP_RECOMMEND",
  "answer": "为你推荐以下几家餐厅...",
  "data": []
}
```

---

## 9.2 自然语言找店接口

```http
POST /ai/shop/recommend
```

请求：

```json
{
  "message": "深圳南山区适合情侣约会的人均 200 以下西餐厅",
  "userId": 10001
}
```

处理：

```text
解析条件
  ↓
调用 search 服务
  ↓
调用 review 服务
  ↓
调用 coupon 服务
  ↓
排序
  ↓
生成推荐理由
```

---

## 9.3 评论总结接口

```http
GET /ai/shop/{shopId}/review-summary
```

响应：

```json
{
  "shopId": 1,
  "advantages": ["环境安静", "适合约会", "服务好"],
  "disadvantages": ["排队久", "停车不方便"],
  "suitableScenes": ["约会", "朋友聚餐"]
}
```

---

## 9.4 AI 工具确认接口

```http
POST /ai/tool/confirm
```

请求：

```json
{
  "confirmToken": "xxx",
  "confirmed": true
}
```

响应：

```json
{
  "success": true,
  "message": "订单创建成功"
}
```

---

## 10. 实现顺序

## 第一阶段：单体黑马点评增强版

### 目标

先在单体结构中补齐核心业务，降低初期复杂度。

### 实现内容

1. 用户登录与鉴权。
2. 商户缓存。
3. 优惠券秒杀。
4. Redis Lua 一人一单。
5. Redis Stream 或 RabbitMQ 异步下单。
6. 评论发布与点赞。
7. Feed 流。
8. 附近商户。
9. Docker Compose 部署。
10. JMeter 压测。

### 产出

- 单体版本可运行。
- Redis 秒杀链路完整。
- 基础业务闭环完成。

---

## 第二阶段：微服务拆分

### 目标

把单体项目拆成更接近企业项目的微服务架构。

### 实现内容

1. 引入 Nacos。
2. 引入 Gateway。
3. 拆分 auth、user、shop、coupon、order 服务。
4. 使用 OpenFeign 完成服务间调用。
5. 引入统一认证过滤器。
6. 引入 Sentinel 限流。
7. 引入 Nacos Config。
8. 提取 common 公共模块。
9. 统一异常和返回结果。
10. 完成服务 Docker Compose 部署。

### 产出

- 微服务版本项目结构。
- 服务注册与服务调用完成。
- 网关统一入口完成。

---

## 第三阶段：搜索与 MQ 完善

### 目标

补齐本地生活系统中的搜索、异步解耦和数据同步能力。

### 实现内容

1. 引入 RabbitMQ / RocketMQ。
2. 引入 Elasticsearch。
3. 建立商户 ES 索引。
4. 商户变更通过 MQ 同步 ES。
5. 实现关键词搜索。
6. 实现综合排序。
7. 实现搜索词统计。
8. 订单超时关闭使用延迟队列。
9. 用户行为通过 MQ 上报。
10. 评论创建后发送 MQ 消息。

### 产出

- 商户搜索服务完成。
- MQ 异步链路完成。
- ES 数据同步完成。

---

## 第四阶段：接入 AI Agent

### 目标

实现自然语言找店和工具调用。

### 实现内容

1. 新建 ai-agent 服务。
2. 接入 Spring AI / LangChain4j。
3. 配置大模型接口。
4. 实现基础 Chat 接口。
5. 实现对话记录存储。
6. 定义 searchShop 工具。
7. 定义 queryCoupons 工具。
8. 定义 queryShopDetail 工具。
9. 定义 summarizeReviews 工具。
10. 实现工具调用日志。

### 产出

- 用户可以自然语言找店。
- AI 可以调用后端真实服务。
- AI 对话和工具调用可追踪。

---

## 第五阶段：实现 RAG 评论知识库

### 目标

实现评论语义检索和智能总结。

### 实现内容

1. 搭建向量数据库。
2. 评论创建后发送 review.created 消息。
3. 消费评论消息。
4. 清洗评论文本。
5. 调用 Embedding 模型。
6. 写入向量数据库。
7. 实现 TopK 语义召回。
8. 拼接 RAG Prompt。
9. 生成评论总结。
10. 缓存热门商户评论总结。

### 产出

- 商户评论总结。
- 商户优缺点分析。
- 场景化判断。

---

## 第六阶段：个性化推荐与安全控制

### 目标

让项目从 demo 升级为可讲工程设计的项目。

### 实现内容

1. 采集用户浏览、收藏、点赞、搜索、下单行为。
2. 用户行为发送 MQ。
3. user 服务更新用户画像。
4. 推荐时结合画像和搜索结果排序。
5. AI 生成个性化推荐解释。
6. 工具按风险等级分类。
7. 高风险工具二次确认。
8. 生成 confirmToken。
9. 校验 confirmToken 后执行操作。
10. 增加 Prompt 注入防护规则。

### 产出

- 个性化推荐闭环。
- AI 下单安全机制。
- 用户画像能力完成。

---

## 11. 项目亮点

## 11.1 微服务架构

项目从单体黑马点评升级为 Spring Cloud Alibaba 微服务架构，使用 Nacos、Gateway、OpenFeign、Sentinel 完成服务注册、路由、远程调用和限流降级。

## 11.2 高并发秒杀

使用 Redis Lua 脚本完成库存扣减和一人一单原子校验，使用 MQ 异步创建订单，并通过数据库唯一索引兜底防止重复下单。

## 11.3 搜索系统

使用 Elasticsearch 实现商户关键词搜索、分类筛选、区域筛选、距离排序和综合排序，商户数据通过 MQ 异步同步。

## 11.4 AI Agent 工具调用

通过 Spring AI / LangChain4j 构建 AI Agent，将商户搜索、优惠券查询、评论总结、秒杀下单等后端服务封装为工具，实现自然语言业务操作。

## 11.5 RAG 评论知识库

将商户评论向量化存储，用户提问时通过语义检索召回相关评论，再由大模型生成商户优缺点总结和场景判断。

## 11.6 个性化推荐

基于用户浏览、收藏、点赞、搜索、下单等行为构建用户画像，结合评分、距离、预算、优惠券和偏好权重进行个性化排序。

## 11.7 AI 安全控制

对下单、取消订单、修改偏好等高风险工具增加二次确认、权限校验、工具白名单和 Prompt 注入防护，避免 AI 误操作。

---

## 12. 项目描述

### 项目名称

基于 Spring Cloud Alibaba + AI Agent 的本地生活点评系统

### 项目描述

本项目基于黑马点评业务进行扩展，构建面向本地生活场景的微服务系统，支持商户搜索、优惠券秒杀、评论互动、Feed 流、用户画像和 AI 智能推荐。系统使用 Spring Cloud Alibaba 完成服务拆分，使用 Redis、MQ、Elasticsearch 提升高并发、异步解耦和搜索能力，并引入 Spring AI / LangChain4j 构建 AI Agent，实现自然语言找店、评论 RAG 总结、智能优惠券推荐和安全工具调用。

### 技术栈

```text
Spring Boot、Spring Cloud Alibaba、Nacos、Gateway、OpenFeign、Sentinel、MyBatis Plus、MySQL、Redis、RabbitMQ、Elasticsearch、Spring AI / LangChain4j、pgvector / Milvus、Docker、JMeter
```

### 亮点

1. 基于 Spring Cloud Alibaba 拆分用户、商户、优惠券、订单、搜索、AI Agent 等服务，使用 Gateway 统一鉴权与限流。
2. 使用 Redis + Lua 实现秒杀库存扣减和一人一单原子校验，使用 MQ 异步创建订单，降低数据库压力。
3. 使用 Elasticsearch 实现商户搜索、条件筛选、距离排序和综合排序，通过 MQ 保证 MySQL 与 ES 数据最终一致。
4. 使用 Spring AI / LangChain4j 实现 AI Agent，将商户搜索、优惠券查询、评论总结等后端能力封装为 Tool。
5. 构建评论 RAG 知识库，对评论进行向量化存储，支持商户优缺点总结、避坑分析和消费场景判断。
6. 基于用户行为日志构建用户画像，结合评分、距离、预算、优惠券和偏好权重实现个性化推荐。
7. 对 AI 下单、取消订单等高风险工具增加二次确认、权限校验和 Prompt 注入防护。

---

## 13. 最小可行版本

如果时间有限，优先实现以下功能：

```text
1. 用户登录
2. 商户查询与缓存
3. 优惠券秒杀
4. Redis Lua 一人一单
5. MQ 异步创建订单
6. 商户 Elasticsearch 搜索
7. AI 自然语言找店
8. AI 评论总结 RAG
9. AI 优惠券推荐
10. 高风险工具二次确认
```