# Spot AI

Spot AI 是一个基于 Spring Boot + Redis + MySQL + React 的本地生活点评系统练习项目。当前已实现 Redis 短信验证码登录、自动注册、Token 登录态、登录拦截器和 React 登录页面。

## 环境要求

- JDK 17
- Maven 3.8+
- Node.js 18+
- MySQL 8+
- Redis 6+

## 1. 初始化数据库

先创建数据库：

```sql
source sql/create_database.sql;
```

再导入第一个逻辑库的数据结构和数据：

```sql
use spotai_0;
source sql/spotai_0.sql;
```

当前登录功能第一版只连接 `spotai_0`。`spotai_1.sql` 是后续分库扩展使用。

## 2. 启动 Redis

确保 Redis 运行在默认地址：

```text
localhost:6379
```

验证码和登录 token 会写入 Redis：

```text
login:code:{phone}
login:token:{token}
```

## 3. 配置后端数据库连接

后端配置文件：

```text
spotai/src/main/resources/application.yml
```

默认连接：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/spotai_0
    username: root
    password: root
```

如果你的 MySQL 用户名或密码不同，请先修改这里。

## 4. 启动后端

进入后端目录：

```bash
cd spotai
mvn spring-boot:run
```

后端默认启动在：

```text
http://localhost:8080
```

可用接口：

```http
POST /user/code?phone=13800138000
POST /user/login
GET /user/me
```

发送验证码后，验证码会打印在后端控制台日志中。

## 5. 启动前端

进入前端目录：

```bash
cd web
npm install
npm run dev
```

前端默认启动在：

```text
http://127.0.0.1:5173
```

Vite 已配置代理：

```text
/user -> http://localhost:8080
```

## 6. 构建与测试

后端测试：

```bash
cd spotai
mvn test
```

前端构建：

```bash
cd web
npm run build
```

## 7. 登录流程

1. 在前端输入手机号。
2. 点击发送验证码。
3. 到后端控制台查看验证码日志。
4. 输入验证码并登录。
5. 登录成功后前端保存 token。
6. 后续请求通过 `Authorization: Bearer {token}` 携带登录态。

更详细的实现原理见：

```text
doc/Spot_AI_登录实现说明.md
```
