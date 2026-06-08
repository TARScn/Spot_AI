# Spot AI 登录实现说明

## 1. 登录方案概览

当前登录模块采用前后端分离的 Redis Token 方案，而不是传统 `JSessionId` Session 方案。

整体链路如下：

```text
发送验证码
  -> 校验手机号
  -> 生成 6 位验证码
  -> Redis 保存验证码
  -> 日志模拟短信发送

验证码登录
  -> 校验手机号和验证码格式
  -> 从 Redis 读取验证码
  -> 校验通过后按手机号查询用户
  -> 用户不存在则自动注册
  -> 生成 token
  -> Redis 保存用户登录态
  -> 前端保存 token

访问受保护接口
  -> 前端携带 Authorization: Bearer token
  -> RefreshTokenInterceptor 读取 Redis 登录态
  -> 写入 UserHolder(ThreadLocal)
  -> LoginInterceptor 校验是否已登录
  -> Controller 从 ThreadLocal 获取当前用户
```

## 2. 短信验证码实现原理

接口：

```http
POST /user/code?phone=13800138000
```

核心实现位于：

- `UserController.code`
- `UserService.sendCode`
- `LogSmsService.sendCode`

处理步骤：

1. 后端使用正则 `^1[3-9]\d{9}$` 校验手机号。
2. 手机号非法时直接返回：

```json
{
  "success": false,
  "data": null,
  "errorMsg": "手机号格式错误"
}
```

3. 手机号合法时生成 6 位数字验证码。
4. 将验证码写入 Redis：

```text
login:code:{phone} -> code
TTL: 5 分钟
```

5. 当前版本不接真实短信平台，而是把验证码打印到后端日志中：

```text
SpotAI login code for phone 13800138000 is 123456
```

这样可以先完成本地开发和接口联调，后续只需要替换 `SmsService` 实现即可接入阿里云、腾讯云等真实短信服务。

## 3. 验证码登录与自动注册

接口：

```http
POST /user/login
Content-Type: application/json

{
  "phone": "13800138000",
  "code": "123456"
}
```

核心实现位于：

- `UserController.login`
- `UserService.login`
- `UserRepository`

处理步骤：

1. 校验手机号格式。
2. 校验验证码必须是 6 位数字。
3. 从 Redis 读取验证码：

```text
login:code:{phone}
```

4. 如果 Redis 中没有验证码，或者验证码不一致，则登录失败。
5. 验证通过后，根据手机号查询用户。
6. 如果用户不存在，则自动创建用户：

```text
nick_name: user_{随机8位}
password: 空字符串
icon: 空字符串
id: MyBatis Plus IdWorker 生成
```

7. 注册时会同时写入用户主表和手机号映射表。
8. 登录成功后生成无横线 UUID token。
9. 将用户摘要信息写入 Redis Hash：

```text
login:token:{token}
  id       -> 用户 ID
  nickName -> 用户昵称
  icon     -> 用户头像

TTL: 30 分钟
```

10. 删除已使用的验证码，避免重复登录。
11. 返回 token：

```json
{
  "success": true,
  "data": "token-value",
  "errorMsg": null
}
```

## 4. 用户分表访问策略

当前第一版只连接 `spotai_0` 数据库，不启用完整分库分表中间件。

用户表仍使用已有的分表结构：

```text
tb_user_0
tb_user_1
tb_user_phone_0
tb_user_phone_1
```

分表规则位于 `ShardUtils`：

```java
Math.floorMod(phone.hashCode(), 2)
```

例如某个手机号被路由到分片 `0`，则：

```text
用户主表: tb_user_0
手机号映射表: tb_user_phone_0
```

查询用户时：

1. 先查 `tb_user_phone_x.phone`。
2. 拿到 `user_id`。
3. 再查 `tb_user_x.id`。

注册用户时：

1. 写入 `tb_user_x`。
2. 写入 `tb_user_phone_x`。

## 5. 登录状态刷新拦截器

`RefreshTokenInterceptor` 负责“识别用户”和“刷新登录有效期”。

处理步骤：

1. 从请求头读取 token：

```http
Authorization: Bearer {token}
```

也兼容小写请求头：

```http
authorization: Bearer {token}
```

2. 如果没有 token，直接放行，让后续拦截器决定是否需要登录。
3. 如果有 token，则读取 Redis：

```text
login:token:{token}
```

4. Redis 中存在用户信息时，组装 `UserDTO`。
5. 将 `UserDTO` 保存到 `UserHolder` 的 `ThreadLocal`。
6. 刷新 token TTL 到 30 分钟。
7. 请求结束后在 `afterCompletion` 中清理 ThreadLocal，避免线程复用导致用户串号。

## 6. 登录校验拦截器

`LoginInterceptor` 负责“是否允许访问受保护接口”。

处理步骤：

1. 放行浏览器 CORS 预检请求 `OPTIONS`。
2. 从 `UserHolder.getUser()` 获取当前用户。
3. 如果用户存在，说明已登录，放行。
4. 如果用户不存在，返回 HTTP 401：

```json
{
  "success": false,
  "data": null,
  "errorMsg": "请先登录"
}
```

当前放行路径在 `WebConfig` 中配置：

```text
/user/code
/user/login
/error
/favicon.ico
/
/index.html
/assets/**
/swagger-ui/**
/v3/api-docs/**
```

其他接口默认需要登录。

## 7. 前端登录页面

前端位于 `web/`，使用 Vite + React。

页面能力：

- 输入手机号。
- 发送验证码。
- 显示 60 秒倒计时。
- 输入验证码登录。
- 登录成功后将 token 保存到 `localStorage`。
- 刷新页面后调用 `/user/me` 恢复当前用户信息。
- 后续请求携带：

```http
Authorization: Bearer {token}
```

Vite 开发代理配置位于 `web/vite.config.js`：

```text
/user -> http://localhost:8080
```

因此本地开发时，前端请求 `/user/code`、`/user/login`、`/user/me` 会代理到 Spring Boot 后端。

## 8. 当前限制

- 当前短信验证码只打印日志，不发送真实短信。
- 当前只连接 `spotai_0`，没有接入完整分库分表中间件。
- 当前 token 主动退出只在前端删除本地 token，后端还没有实现 `/user/logout` 删除 Redis token。
- 当前登录用户只返回 `id`、`nickName`、`icon`，不返回手机号等敏感信息。
