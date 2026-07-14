# Spot AI 登录实现说明

> 核对日期：2026-07-14  
> 核对范围：`UserController`、`UserService`、`UserRepository`、登录拦截器、前端登录弹窗。

## 1. 当前登录方案

当前项目使用邮箱验证码登录，不是手机号验证码登录。

流程：

1. 用户输入邮箱。
2. 前端调用 `POST /user/code?email=...`。
3. 后端生成 6 位验证码，通过邮件发送。
4. 验证码写入 Redis `login:code:{email}`。
5. 前端提交邮箱和验证码，调用 `POST /user/login`。
6. 后端校验验证码。
7. 用户不存在时自动注册。
8. 生成 token，写入 Redis `login:token:{token}`。
9. 前端保存 token，后续请求放入 `Authorization` 请求头。

当前没有 JWT，也没有 Sa-Token。

## 2. 接口

### 2.1 发送验证码

```http
POST /user/code?email=user@example.com
```

返回：

```json
{
  "success": true,
  "data": null,
  "errorMsg": null
}
```

### 2.2 登录

```http
POST /user/login
Content-Type: application/json
```

请求体：

```json
{
  "email": "user@example.com",
  "code": "123456"
}
```

返回中包含 token 和用户信息。

### 2.3 当前用户

```http
GET /user/me
Authorization: {token}
```

### 2.4 用户聚合信息

```http
GET /user/profile
Authorization: {token}
```

我的页面使用该接口展示用户资料、笔记、评价等聚合数据。

## 3. Redis Key

| Key | 说明 |
| --- | --- |
| `login:code:{email}` | 邮箱验证码，TTL 由 `spotai.auth.code-ttl-minutes` 控制 |
| `login:token:{token}` | 登录态 Hash，TTL 由 `spotai.auth.token-ttl-minutes` 控制 |

`RefreshTokenInterceptor` 会在请求进入时读取 token，并刷新登录态 TTL。

## 4. MySQL 表

| 表 | 说明 |
| --- | --- |
| `tb_user_0`、`tb_user_1` | 用户主表 |
| `tb_user_email_0`、`tb_user_email_1` | 邮箱到用户 ID 的映射 |

当前没有 `tb_user_phone_0/1`，也没有 `tb_user_info_0/1`。

## 5. 用户分表

`UserRepository` 根据用户 ID 或邮箱路由到分表：

- 用户主表：`tb_user_0/1`
- 邮箱映射表：`tb_user_email_0/1`

登录时先查邮箱映射。如果不存在，则创建用户并写入映射。

## 6. 拦截器

| 拦截器 | 作用 |
| --- | --- |
| `RefreshTokenInterceptor` | 从 Redis 读取登录态、刷新 TTL、写入 `UserHolder` |
| `LoginInterceptor` | 对需要登录的接口做拦截 |

未登录时，后端返回失败结果；前端根据业务场景弹出登录弹窗或提示。

## 7. 前端行为

- 登录入口在顶部导航和需要登录的操作中触发。
- 登录后 token 保存到前端。
- 退出登录当前主要由前端删除本地 token，并清理用户态。
- AI 对话：游客历史只保留在前端；登录用户历史保存到后端。

## 8. 当前限制

- 后端没有 `/user/logout` 接口。
- 邮件发送依赖 `MAIL_HOST`、`MAIL_USERNAME`、`MAIL_PASSWORD` 配置。
- 验证码发送没有复杂风控，只依赖 TTL 和基础校验。
- 公开部署前不要提交 `local-secrets.properties`。
