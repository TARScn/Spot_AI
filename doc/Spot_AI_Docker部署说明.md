# Spot AI Docker 部署说明

本文档说明如何把 Spot AI 制作为 Docker 镜像并部署到云服务器。

## 1. 镜像结构

项目拆成两个应用镜像：

- `spotai-backend`：Spring Boot 后端，端口 `8080`
- `spotai-web`：Nginx + Vite 静态资源，端口 `80`，并反向代理后端 API

生产依赖服务由 `docker-compose.prod.yml` 编排：

- MySQL 8.0
- Redis Stack
- MinIO
- Spot AI backend
- Spot AI web

## 2. 本地构建镜像

在项目根目录执行，先替换镜像仓库地址和版本号：

```powershell
$REGISTRY="registry.example.com/spotai"
$TAG="1.0.0"

docker build -f spotai/Dockerfile -t "$REGISTRY/spotai-backend:$TAG" spotai
docker build -f web/Dockerfile -t "$REGISTRY/spotai-web:$TAG" web
```

如果前端依赖下载较慢，可以指定 npm 镜像：

```powershell
docker build `
  --build-arg NPM_REGISTRY=https://registry.npmmirror.com `
  -f web/Dockerfile `
  -t "$REGISTRY/spotai-web:$TAG" web
```

如果使用 Docker Hub，例如用户名是 `yourname`：

```powershell
$REGISTRY="docker.io/yourname"
$TAG="1.0.0"

docker build -f spotai/Dockerfile -t "$REGISTRY/spotai-backend:$TAG" spotai
docker build -f web/Dockerfile -t "$REGISTRY/spotai-web:$TAG" web
```

## 3. 推送镜像

```powershell
docker login registry.example.com
docker push "$REGISTRY/spotai-backend:$TAG"
docker push "$REGISTRY/spotai-web:$TAG"
```

Docker Hub 则使用：

```powershell
docker login
docker push "$REGISTRY/spotai-backend:$TAG"
docker push "$REGISTRY/spotai-web:$TAG"
```

## 4. 准备服务器目录

在云服务器上创建目录：

```bash
mkdir -p /opt/spot-ai
cd /opt/spot-ai
```

把以下文件上传到服务器：

- `docker-compose.prod.yml`
- `deploy.env.example`，上传后改名为 `.env`
- `sql/` 目录，用于首次初始化数据库

`.env` 中至少要修改：

- `SPOTAI_BACKEND_IMAGE`
- `SPOTAI_WEB_IMAGE`
- `MYSQL_PASSWORD`
- `REDIS_PASSWORD`
- `MINIO_SECRET_KEY`
- `MINIO_EXTERNAL_ENDPOINT`
- `MAIL_USERNAME`
- `MAIL_PASSWORD`
- `DEEPSEEK_API_KEY`
- `DASHSCOPE_API_KEY`

## 5. 启动依赖与应用

```bash
docker compose -f docker-compose.prod.yml --env-file .env pull
docker compose -f docker-compose.prod.yml --env-file .env up -d
```

查看状态：

```bash
docker compose -f docker-compose.prod.yml --env-file .env ps
docker logs -f spotai-backend
docker logs -f spotai-web
```

## 6. 首次初始化数据库

第一次启动 MySQL 后执行：

```bash
docker exec -i spotai-mysql mysql -uroot -p"$MYSQL_PASSWORD" < sql/create_database.sql
docker exec -i spotai-mysql mysql -uroot -p"$MYSQL_PASSWORD" spotai_0 < sql/spotai_0.sql
docker exec -i spotai-mysql mysql -uroot -p"$MYSQL_PASSWORD" spotai_1 < sql/spotai_1.sql
```

如果你已经有旧库，还要按需执行迁移：

```bash
docker exec -i spotai-mysql mysql -uroot -p"$MYSQL_PASSWORD" spotai_0 < sql/migrate_drop_unused_tables.sql
```

注意：`docker-compose.prod.yml` 默认后端连接 `spotai_0`，项目内分片表通过 SQL 和 `ShardUtils` 访问。

## 7. 访问地址

- 前端页面：`http://服务器IP/`
- 后端接口：`http://服务器IP:8080/`
- MinIO API：`http://服务器IP:19000`
- MinIO 控制台：`http://服务器IP:19001`

生产环境建议再加一层 Nginx/云负载均衡做 HTTPS，把 `80/443` 暴露给用户，数据库、Redis、MinIO 控制台限制安全组来源。

## 8. 更新版本

本地重新构建并推送新 tag：

```powershell
$TAG="1.0.1"
docker build -f spotai/Dockerfile -t "$REGISTRY/spotai-backend:$TAG" spotai
docker build -f web/Dockerfile -t "$REGISTRY/spotai-web:$TAG" web
docker push "$REGISTRY/spotai-backend:$TAG"
docker push "$REGISTRY/spotai-web:$TAG"
```

服务器修改 `.env` 中镜像 tag 后执行：

```bash
docker compose -f docker-compose.prod.yml --env-file .env pull
docker compose -f docker-compose.prod.yml --env-file .env up -d
```

## 9. 回滚

把 `.env` 中 `SPOTAI_BACKEND_IMAGE` 和 `SPOTAI_WEB_IMAGE` 改回上一个稳定 tag：

```bash
docker compose -f docker-compose.prod.yml --env-file .env pull
docker compose -f docker-compose.prod.yml --env-file .env up -d
```

## 10. 部署注意事项

- 不要把 `.env` 上传到 Git。
- 云服务器安全组只建议开放 `80/443`，调试期可临时开放 `8080`、`19000`、`19001`。
- Redis 和 MySQL 不建议对公网开放；如果必须开放，要限制来源 IP。
- 默认 `SPOTAI_MQ_ENABLED=false`、`SPOTAI_VOUCHER_MQ_ENABLED=false`，表示不启动 RocketMQ 消费链路。要部署完整异步业务事件和秒杀下单，再补 RocketMQ 服务并改为 `true`。
- `SPOTAI_AI_ENABLED=true` 后，评价摘要会调用 DashScope/DeepSeek，注意 API 额度。
