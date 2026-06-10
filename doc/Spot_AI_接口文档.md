# Spot AI 接口文档

## 1. 文档说明

本文分为两部分：

- **已实现接口**：当前 Spot AI 后端已经实现并可以调用的接口。
- **参考接口**：参考 `cs001020/hmdp` 项目提取的接口清单，用于后续扩展 Spot AI 功能时对齐设计。

参考项目：

```text
https://github.com/cs001020/hmdp
```

参考版本：

```text
b9026b6da2274f4fc7f419aceb6d84c8e24222b7
```

当前后端默认地址：

```text
http://localhost:8080
```

当前前端开发地址：

```text
http://127.0.0.1:5173
```

## 2. 通用约定

### 2.1 统一响应

后端统一返回：

```json
{
  "success": true,
  "data": {},
  "errorMsg": null
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `success` | boolean | 请求是否处理成功。 |
| `data` | any | 成功时返回的数据；无数据时为 `null`。 |
| `errorMsg` | string | 失败原因；成功时为 `null`。 |

成功示例：

```json
{
  "success": true,
  "data": "token-value",
  "errorMsg": null
}
```

失败示例：

```json
{
  "success": false,
  "data": null,
  "errorMsg": "手机号格式错误"
}
```

### 2.2 登录鉴权

登录成功后，后端返回 token。前端访问需要登录的接口时，需要携带请求头：

```http
Authorization: Bearer {token}
```

后端鉴权流程：

1. `RefreshTokenInterceptor` 读取 `Authorization` 或 `authorization` 请求头。
2. 根据 token 查询 Redis：`login:token:{token}`。
3. token 有效时，将用户信息写入 `UserHolder` 的 `ThreadLocal`。
4. 刷新 token 过期时间，默认 30 分钟。
5. `LoginInterceptor` 校验当前线程是否存在登录用户。
6. 未登录时返回 HTTP `401`。

未登录响应：

```json
{
  "success": false,
  "data": null,
  "errorMsg": "请先登录"
}
```

### 2.3 CORS

当前后端允许前端来源：

```text
http://localhost:5173
```

允许方法：

```text
GET, POST, PUT, DELETE, OPTIONS
```

浏览器 `OPTIONS` 预检请求会被登录拦截器直接放行。

## 3. 接口总览

### 3.1 已实现接口

| 模块 | 方法 | 路径 | 是否需要登录 | 说明 |
|---|---|---|---|---|
| 用户 | `POST` | `/user/code` | 否 | 发送短信验证码，当前通过日志模拟短信。 |
| 用户 | `POST` | `/user/login` | 否 | 手机号验证码登录；用户不存在时自动注册。 |
| 用户 | `GET` | `/user/me` | 是 | 获取当前登录用户信息。 |
| 商户 | `GET` | `/shop/{id}` | 否 | 根据 ID 查询商户详情，使用 Redis 缓存。 |
| 商户 | `PUT` | `/shop` | 否 | 更新商户信息，更新数据库后删除 Redis 缓存。 |

### 3.2 参考接口状态

| 模块 | 状态 |
|---|---|
| 用户扩展 | 参考接口已列出，部分已实现。 |
| 商户 | 参考接口已列出，当前未实现。 |
| 商户分类 | 参考接口已列出，当前未实现。 |
| 探店博客 | 参考接口已列出，当前未实现。 |
| 关注 | 参考接口已列出，当前未实现。 |
| 优惠券 | 参考接口已列出，当前未实现。 |
| 秒杀订单 | 参考接口已列出，当前未实现。 |
| 文件上传 | 参考接口已列出，当前未实现。 |
| 博客评论 | 参考项目 Controller 暂无具体方法。 |

## 4. 已实现接口详情

### 4.1 发送验证码

```http
POST /user/code
```

是否需要登录：否

请求参数类型：Query

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `phone` | string | 是 | 中国大陆 11 位手机号。 |

手机号校验规则：

```text
^1[3-9]\d{9}$
```

请求示例：

```http
POST /user/code?phone=13800138000
```

成功响应：

```json
{
  "success": true,
  "data": null,
  "errorMsg": null
}
```

常见失败：

| 场景 | 响应 |
|---|---|
| 手机号格式错误 | `{"success":false,"data":null,"errorMsg":"手机号格式错误"}` |
| 缺少 `phone` 参数 | `{"success":false,"data":null,"errorMsg":"缺少参数：phone"}` |

后端行为：

1. 校验手机号。
2. 生成 6 位数字验证码。
3. 写入 Redis：`login:code:{phone}`，TTL 为 5 分钟。
4. 通过日志模拟短信发送。

验证码日志示例：

```text
SpotAI login code for phone 13800138000 is 123456
```

### 4.2 手机号验证码登录

```http
POST /user/login
```

是否需要登录：否

请求参数类型：JSON Body

请求头：

```http
Content-Type: application/json
```

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `phone` | string | 是 | 中国大陆 11 位手机号。 |
| `code` | string | 是 | 6 位短信验证码。 |

请求示例：

```json
{
  "phone": "13800138000",
  "code": "123456"
}
```

成功响应：

```json
{
  "success": true,
  "data": "26c703b2b65646668f6b7f93e56f1c94",
  "errorMsg": null
}
```

`data` 为登录 token。

常见失败：

| 场景 | 响应 |
|---|---|
| 手机号格式错误 | `{"success":false,"data":null,"errorMsg":"手机号格式错误"}` |
| 验证码格式错误 | `{"success":false,"data":null,"errorMsg":"验证码格式错误"}` |
| 验证码错误或过期 | `{"success":false,"data":null,"errorMsg":"验证码错误或已过期"}` |

后端行为：

1. 校验手机号和验证码格式。
2. 从 Redis 读取 `login:code:{phone}`。
3. 验证码不存在或不匹配时登录失败。
4. 验证通过后，根据手机号查询用户。
5. 用户不存在时自动注册，写入 `tb_user_0/1` 和 `tb_user_phone_0/1`。
6. 生成 token。
7. 将用户摘要写入 Redis Hash：`login:token:{token}`。
8. 删除已使用的验证码。
9. 返回 token。

### 4.3 获取当前登录用户

```http
GET /user/me
```

是否需要登录：是

请求头：

```http
Authorization: Bearer {token}
```

成功响应：

```json
{
  "success": true,
  "data": {
    "id": 10001,
    "nickName": "user_ab12cd34",
    "icon": ""
  },
  "errorMsg": null
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | number | 用户 ID。 |
| `nickName` | string | 用户昵称。 |
| `icon` | string | 用户头像地址，当前可能为空。 |

未登录响应：

HTTP 状态码：`401`

```json
{
  "success": false,
  "data": null,
  "errorMsg": "请先登录"
}
```

### 4.4 查询商户详情

```http
GET /shop/{id}
```

是否需要登录：否

请求参数类型：Path

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | number | 是 | 商户 ID。 |

请求示例：

```http
GET /shop/1
```

成功响应：

```json
{
  "success": true,
  "data": {
    "id": 1,
    "name": "103茶餐厅",
    "typeId": 1,
    "images": "https://example.com/shop.jpg",
    "area": "大关",
    "address": "金华路锦昌文华苑29号",
    "x": 120.149192,
    "y": 30.316078,
    "avgPrice": 80,
    "sold": 4215,
    "comments": 3035,
    "score": 37,
    "openHours": "10:00-22:00",
    "createTime": "2021-12-22T10:10:39",
    "updateTime": "2022-01-13T09:32:19"
  },
  "errorMsg": null
}
```

常见失败：

| 场景 | 响应 |
|---|---|
| 商户 ID 不合法 | `{"success":false,"data":null,"errorMsg":"商户ID不合法"}` |
| 商户不存在 | `{"success":false,"data":null,"errorMsg":"商户不存在"}` |

Redis 缓存规则：

说明：商户缓存的 JSON 序列化、TTL 写入、空值缓存、逻辑过期和互斥锁重建逻辑统一封装在 `CacheClient` 中，业务服务只负责传入 key、类型和数据库回源函数。

| Key | Value | TTL | 说明 |
|---|---|---|---|
| `cache:shop:{id}` | `{ "data": 商户JSON, "expireTime": 逻辑过期时间 }` | 不设置物理 TTL | 正常商户详情缓存，使用逻辑过期解决缓存击穿。 |
| `cache:shop:{id}` | 空字符串 | 2 分钟 | 商户不存在时缓存空值，降低缓存穿透风险。 |
| `lock:shop:{id}` | `1` | 10 秒 | 逻辑过期后重建缓存用的互斥锁。 |

后端行为：

1. 校验商户 ID。
2. 查询 Redis `cache:shop:{id}`。
3. Redis 命中商户 JSON 且逻辑未过期时直接反序列化返回。
4. Redis 命中空字符串时直接返回“商户不存在”。
5. Redis 命中商户 JSON 但逻辑已过期时，先返回旧数据。
6. 逻辑已过期时尝试获取 `lock:shop:{id}`。
7. 获取锁成功的请求异步查询 MySQL 并重建缓存，最后释放锁。
8. 获取锁失败的请求不查数据库，直接返回旧数据。
9. Redis 未命中时查询 MySQL `tb_shop`。
10. MySQL 存在商户时写入逻辑过期缓存并返回。
11. MySQL 不存在商户时写入短 TTL 空值并返回失败。

### 4.5 更新商户信息

```http
PUT /shop
```

是否需要登录：否

说明：当前项目暂未实现管理员角色，因此接口暂不做管理员鉴权。后续接入权限体系后，建议只允许管理员或商户所属用户调用。

请求参数类型：JSON Body

请求头：

```http
Content-Type: application/json
```

请求示例：

```json
{
  "id": 1,
  "name": "103茶餐厅",
  "typeId": 1,
  "images": "https://example.com/shop.jpg",
  "area": "大关",
  "address": "金华路锦昌文华苑29号",
  "x": 120.149192,
  "y": 30.316078,
  "avgPrice": 80,
  "sold": 4215,
  "comments": 3035,
  "score": 37,
  "openHours": "10:00-22:00"
}
```

成功响应：

```json
{
  "success": true,
  "data": null,
  "errorMsg": null
}
```

常见失败：

| 场景 | 响应 |
|---|---|
| 商户 ID 不合法 | `{"success":false,"data":null,"errorMsg":"商户ID不合法"}` |
| 商户不存在 | `{"success":false,"data":null,"errorMsg":"商户不存在"}` |

缓存更新策略：

1. 先更新 MySQL `tb_shop`。
2. 更新成功后删除 Redis：`cache:shop:{id}`。
3. 下一次查询 `/shop/{id}` 时回源数据库，并重新写入逻辑过期缓存。

## 5. 参考接口详情

以下接口来自参考 HMDP 项目，用于 Spot AI 后续功能扩展。状态为“已实现”的接口表示当前 Spot AI 已有对应实现；状态为“未实现”的接口只作为设计参考。

### 5.1 用户模块

基础路径：`/user`

| 方法 | 路径 | 是否需要登录 | 参数 | 说明 | Spot AI 状态 |
|---|---|---|---|---|---|
| `POST` | `/user/code` | 否 | Query: `phone` | 发送手机验证码。 | 已实现 |
| `POST` | `/user/login` | 否 | Body: `phone`, `code` | 手机号验证码登录。 | 已实现 |
| `POST` | `/user/logout` | 是 | 无 | 退出登录；参考项目中为 TODO。 | 未实现 |
| `GET` | `/user/me` | 是 | 无 | 获取当前登录用户。 | 已实现 |
| `GET` | `/user/info/{id}` | 否/可选 | Path: `id` | 查询用户详细资料。 | 未实现 |
| `GET` | `/user/{id}` | 否/可选 | Path: `id` | 根据用户 ID 查询用户公开信息。 | 未实现 |
| `POST` | `/user/sign` | 是 | 无 | 用户每日签到。 | 未实现 |
| `GET` | `/user/sign/count` | 是 | 无 | 查询当前用户连续签到天数。 | 未实现 |

### 5.2 商户模块

基础路径：`/shop`

| 方法 | 路径 | 是否需要登录 | 参数 | 说明 | Spot AI 状态 |
|---|---|---|---|---|---|
| `GET` | `/shop/{id}` | 否 | Path: `id` | 根据 ID 查询商户详情。 | 已实现 |
| `POST` | `/shop` | 建议管理员 | Body: 商户对象 | 新增商户。 | 未实现 |
| `PUT` | `/shop` | 建议管理员 | Body: 商户对象 | 更新商户信息。 | 已实现 |
| `GET` | `/shop/of/type` | 否 | Query: `typeId`, `current`, `x`, `y` | 根据商户类型分页查询；可传经纬度做附近商户查询。 | 未实现 |
| `GET` | `/shop/of/name` | 否 | Query: `name`, `current` | 根据商户名称关键字分页查询。 | 未实现 |

### 5.3 商户分类模块

基础路径：`/shop-type`

| 方法 | 路径 | 是否需要登录 | 参数 | 说明 | Spot AI 状态 |
|---|---|---|---|---|---|
| `GET` | `/shop-type/list` | 否 | 无 | 查询商户分类列表，通常按 `sort` 排序。 | 未实现 |

### 5.4 探店博客模块

基础路径：`/blog`

| 方法 | 路径 | 是否需要登录 | 参数 | 说明 | Spot AI 状态 |
|---|---|---|---|---|---|
| `POST` | `/blog` | 是 | Body: 博客对象 | 发布探店笔记。 | 未实现 |
| `PUT` | `/blog/like/{id}` | 是 | Path: `id` | 点赞或取消点赞探店笔记。 | 未实现 |
| `GET` | `/blog/of/me` | 是 | Query: `current` | 查询当前登录用户的探店笔记。 | 未实现 |
| `GET` | `/blog/hot` | 否 | Query: `current` | 查询热门探店笔记。 | 未实现 |
| `GET` | `/blog/{id}` | 否 | Path: `id` | 根据 ID 查询探店笔记详情。 | 未实现 |
| `GET` | `/blog/likes/{id}` | 否 | Path: `id` | 查询某篇笔记点赞用户列表。 | 未实现 |
| `GET` | `/blog/of/user` | 否 | Query: `id`, `current` | 查询指定用户的探店笔记。 | 未实现 |
| `GET` | `/blog/of/follow` | 是 | Query: `lastId`, `offset` | 查询关注流，使用滚动分页。 | 未实现 |

### 5.5 关注模块

基础路径：`/follow`

| 方法 | 路径 | 是否需要登录 | 参数 | 说明 | Spot AI 状态 |
|---|---|---|---|---|---|
| `PUT` | `/follow/{id}/{isFollow}` | 是 | Path: `id`, `isFollow` | 关注或取消关注指定用户。 | 未实现 |
| `GET` | `/follow/or/not/{id}` | 是 | Path: `id` | 判断当前用户是否已关注指定用户。 | 未实现 |
| `GET` | `/follow/common/{id}` | 是 | Path: `id` | 查询当前用户与指定用户的共同关注。 | 未实现 |

### 5.6 优惠券模块

基础路径：`/voucher`

| 方法 | 路径 | 是否需要登录 | 参数 | 说明 | Spot AI 状态 |
|---|---|---|---|---|---|
| `POST` | `/voucher` | 建议管理员 | Body: 优惠券对象 | 新增普通优惠券。 | 未实现 |
| `POST` | `/voucher/seckill` | 建议管理员 | Body: 秒杀券对象 | 新增秒杀优惠券，同时写入秒杀库存。 | 未实现 |
| `GET` | `/voucher/list/{shopId}` | 否 | Path: `shopId` | 查询指定商户的优惠券列表。 | 未实现 |

秒杀券请求体参考：

```json
{
  "shopId": 1,
  "title": "100元代金券",
  "subTitle": "周一至周日均可使用",
  "rules": "全场通用",
  "payValue": 8000,
  "actualValue": 10000,
  "type": 1,
  "stock": 100,
  "beginTime": "2026-06-09T00:00:00",
  "endTime": "2026-06-10T20:00:00"
}
```

### 5.7 秒杀订单模块

基础路径：`/voucher-order`

| 方法 | 路径 | 是否需要登录 | 参数 | 说明 | Spot AI 状态 |
|---|---|---|---|---|---|
| `POST` | `/voucher-order/seckill/{id}` | 是 | Path: `id` | 抢购秒杀券，成功后创建优惠券订单。 | 未实现 |

Spot AI 已经有 Redis 全局 ID 生成器，后续实现该接口时可用于生成 `tb_voucher_order.id`：

```java
redisIdWorker.nextId("voucher_order")
```

### 5.8 文件上传模块

基础路径：`/upload`

| 方法 | 路径 | 是否需要登录 | 参数 | 说明 | Spot AI 状态 |
|---|---|---|---|---|---|
| `POST` | `/upload/blog` | 是 | FormData: `file` | 上传探店笔记图片。 | 未实现 |
| `GET` | `/upload/blog/delete` | 是 | Query: `name` | 删除探店笔记图片。 | 未实现 |

上传请求类型：

```http
Content-Type: multipart/form-data
```

### 5.9 博客评论模块

基础路径：`/blog-comments`

参考项目中该 Controller 暂未提供具体方法。Spot AI 后续如果实现评论能力，可以结合当前数据库中的 `tb_blog_comments`，或者使用独立点评表 `tb_review` 设计接口。

## 6. 当前未实现能力汇总

| 能力 | 说明 |
|---|---|
| 退出登录 | 暂未提供 `/user/logout`，前端目前只删除本地 token。 |
| 商户列表和附近查询 | 已实现商户详情查询和商户更新；新增、列表、分类分页和附近查询未实现。 |
| 商户分类 | 数据表已存在，接口未实现。 |
| 探店博客 | 数据表已存在，接口未实现。 |
| 关注关系 | 数据表已存在，接口未实现。 |
| 优惠券秒杀 | 数据表和 Redis ID 工具已准备，接口未实现。 |
| 订单查询 | 数据表已存在，接口未实现。 |
| 文件上传 | 技术文档规划使用 MinIO，接口未实现。 |
| AI 对话 | 技术文档有规划，接口未实现。 |
