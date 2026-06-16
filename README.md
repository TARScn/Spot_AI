# Spot AI

Spot AI 是一个基于 Spring Boot、MySQL、Redis、RocketMQ 和 React 的本地生活点评系统练习项目。当前包含手机号验证码登录、Redis 缓存、附近商户 GEO 查询、探店笔记、关注 Feed、优惠券秒杀下单、RocketMQ 异步订单处理等功能。

## 环境要求

- JDK 17
- Maven 3.8+
- Node.js 18+
- MySQL 8+
- Redis 6+
- RocketMQ 5.x
- 可选：MinIO，用于文件上传

## 初始化数据库

在 MySQL 中执行：

```sql
source sql/create_database.sql;
use spotai_0;
source sql/spotai_0.sql;
```

当前后端默认连接 `spotai_0`。

## 配置说明

后端配置文件：

```text
spotai/src/main/resources/application.yml
```

常用默认值：

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/spotai_0
    username: root
    password: 000000
  data:
    redis:
      host: localhost
      port: 6379
      password: 540788

rocketmq:
  name-server: localhost:9876
```

这些配置也支持环境变量覆盖，例如：

```powershell
$env:MYSQL_PASSWORD="你的密码"
$env:REDIS_PASSWORD="你的密码"
$env:ROCKETMQ_NAME_SERVER="localhost:9876"
```

## 启动项目

建议启动顺序：MySQL -> Redis -> RocketMQ -> 后端 -> 前端。

### 1. 启动 Redis

如果 Redis 安装在 WSL 中：

```bash
sudo service redis-server start
redis-cli -a 你的密码 ping
```

返回 `PONG` 表示 Redis 可用。

### 2. 启动 RocketMQ

在 WSL 的 RocketMQ 安装目录执行，示例：

```bash
cd ~/apps/rocketmq

nohup sh bin/mqnamesrv > /tmp/rocketmq-namesrv.log 2>&1 &
nohup sh bin/mqbroker -n 127.0.0.1:9876 -c conf/broker-local.conf > /tmp/rocketmq-broker.log 2>&1 &

jps
```

看到 `NamesrvStartup` 和 `BrokerStartup` 表示 RocketMQ 已启动。

### 3. 启动后端

在项目根目录执行：

```powershell
cd spotai
mvn spring-boot:run "-Dspring-boot.run.arguments=--spotai.minio.initialize-bucket=false"
```

后端地址：

```text
http://localhost:8080
```

如果已启动 MinIO，并希望后端初始化 bucket，可以去掉 `--spotai.minio.initialize-bucket=false`。

### 4. 启动前端

在项目根目录执行：

```powershell
cd web
npm install
npm run dev -- --host 127.0.0.1
```

前端地址：

```text
http://127.0.0.1:5173
```

## 常用验证

后端健康验证：

```powershell
Invoke-RestMethod http://localhost:8080/shop-type/list
```

前端验证：

```powershell
Invoke-WebRequest http://127.0.0.1:5173/
```

刷新商户 GEO 数据：

```powershell
Invoke-RestMethod -Method Put http://localhost:8080/shop/geo/load
```

验证码发送后，可在后端控制台或 `spotai-backend.log` 中查看日志模拟的验证码。

## 关闭项目

### 1. 关闭前端

如果是在终端中运行 `npm run dev`，按 `Ctrl + C`。

如果是后台进程，可在 PowerShell 中按端口关闭：

```powershell
$pids = Get-NetTCPConnection -State Listen -LocalPort 5173 -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique
$pids | ForEach-Object { Stop-Process -Id $_ -Force }
```

### 2. 关闭后端

如果是在终端中运行 `mvn spring-boot:run`，按 `Ctrl + C`。

如果是后台进程，可按端口关闭：

```powershell
$pids = Get-NetTCPConnection -State Listen -LocalPort 8080 -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique
$pids | ForEach-Object { Stop-Process -Id $_ -Force }
```

### 3. 关闭 RocketMQ

如果 RocketMQ 在 WSL 中运行：

```bash
jps
kill -9 <BrokerStartup进程ID>
kill -9 <NamesrvStartup进程ID>
```

也可以在 Windows PowerShell 中直接停止整个 Ubuntu WSL 实例：

```powershell
wsl --terminate Ubuntu
```

注意：这会同时停止 WSL 中运行的 Redis、RocketMQ 和其他后台服务。

### 4. 关闭 Redis

如果 Redis 在 WSL 中运行：

```bash
sudo service redis-server stop
```

如果你使用 `wsl --terminate Ubuntu`，Redis 会随 WSL 一起停止。

### 5. 一键清理本项目常见后台进程

在项目根目录运行：

```powershell
$ports = 8080,5173,6379,9876,10911,10909,10912
Get-NetTCPConnection -State Listen -LocalPort $ports -ErrorAction SilentlyContinue |
  Select-Object -ExpandProperty OwningProcess -Unique |
  ForEach-Object { Stop-Process -Id $_ -Force -ErrorAction SilentlyContinue }
```

如果 Redis/RocketMQ 在 WSL 中：

```powershell
wsl --terminate Ubuntu
```

## 构建与测试

后端编译：

```powershell
cd spotai
mvn -q -DskipTests compile
```

后端测试：

```powershell
cd spotai
mvn test
```

前端构建：

```powershell
cd web
npm run build
```

## 主要接口

```http
POST /user/code?phone=13800138000
POST /user/login
GET  /user/me
GET  /shop-type/list
GET  /shop/of/type?typeId=1&current=1&x=108.916860&y=34.229210
GET  /shop/{id}
GET  /blog/of/shop?id={shopId}
POST /voucher-order/seckill/{voucherId}
```

更完整的接口说明见：

```text
doc/Spot_AI_接口文档.md
```
