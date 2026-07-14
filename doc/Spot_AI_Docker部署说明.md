# Spot AI Docker 部署说明

> 核对日期：2026-07-14  
> 核对范围：`docker-compose.prod.yml`、`spotai/Dockerfile`、`web/Dockerfile`、`deploy.env.example`。

## 1. 镜像结构

项目生产部署拆成两个应用镜像：

| 镜像 | 说明 | 容器内端口 |
| --- | --- | --- |
| `spotai-backend` | Spring Boot 后端 | `8080` |
| `spotai-web` | Nginx 托管 Vite 构建产物，并反向代理 API | `80` |

生产依赖由 `docker-compose.prod.yml` 编排：

- MySQL 8.0
- Redis Stack
- MinIO
- Spot AI Backend
- Spot AI Web

RocketMQ 当前没有放进 `docker-compose.prod.yml`。如需启用完整异步链路，需要额外部署 RocketMQ，并把 `.env` 中的 MQ 开关改为 `true`。

## 2. 本地构建镜像

```powershell
docker build -f spotai/Dockerfile -t spotai-backend:latest spotai
docker build -f web/Dockerfile -t spotai-web:latest web
```

前端依赖下载慢时可指定 npm 镜像：

```powershell
docker build `
  --build-arg NPM_REGISTRY=https://registry.npmmirror.com `
  -f web/Dockerfile `
  -t spotai-web:latest web
```

## 3. 推送到镜像仓库

```powershell
$REGISTRY="registry.example.com/spotai"
$TAG="1.0.0"

docker tag spotai-backend:latest "$REGISTRY/spotai-backend:$TAG"
docker tag spotai-web:latest "$REGISTRY/spotai-web:$TAG"

docker login registry.example.com
docker push "$REGISTRY/spotai-backend:$TAG"
docker push "$REGISTRY/spotai-web:$TAG"
```

## 4. 服务器目录

建议服务器目录：

```bash
mkdir -p /opt/spot-ai
cd /opt/spot-ai
```

需要上传：

- `docker-compose.prod.yml`
- `deploy.env.example`，上传后复制为 `.env`
- `sql/` 目录

`.env` 至少需要配置：

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
- `SPOTAI_AI_ENABLED`

## 5. 启动服务

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

## 6. 初始化数据库

首次启动 MySQL 后执行：

```bash
docker exec -i spotai-mysql mysql -uroot -p"$MYSQL_PASSWORD" < sql/create_database.sql
docker exec -i spotai-mysql mysql -uroot -p"$MYSQL_PASSWORD" spotai_0 < sql/spotai_0.sql
docker exec -i spotai-mysql mysql -uroot -p"$MYSQL_PASSWORD" spotai_1 < sql/spotai_1.sql
```

如果是旧库升级，根据当前功能按需执行：

```bash
docker exec -i spotai-mysql mysql -uroot -p"$MYSQL_PASSWORD" spotai_0 < sql/migrate_ai_agent_memory.sql
docker exec -i spotai-mysql mysql -uroot -p"$MYSQL_PASSWORD" spotai_0 < sql/migrate_ai_tool_call_log.sql
docker exec -i spotai-mysql mysql -uroot -p"$MYSQL_PASSWORD" spotai_0 < sql/migrate_review_summary_store.sql
docker exec -i spotai-mysql mysql -uroot -p"$MYSQL_PASSWORD" spotai_0 < sql/migrate_shop_items.sql
docker exec -i spotai-mysql mysql -uroot -p"$MYSQL_PASSWORD" spotai_0 < sql/seed_shop_items.sql
```

## 7. 访问地址

默认 compose 端口由 `.env` 控制：

| 服务 | 默认外部端口 | 说明 |
| --- | --- | --- |
| Web | `80` | 前端入口 |
| Backend | `8080` | 后端 API，生产建议只通过 Web/Nginx 代理访问 |
| MySQL | `3306` | 生产不建议公网开放 |
| Redis | `6379` | 生产不建议公网开放 |
| MinIO API | `19000` | 图片访问和对象存储 API |
| MinIO Console | `19001` | 管理控制台，生产不建议公网开放 |

## 8. MQ 开关

当前生产模板默认：

```properties
SPOTAI_MQ_ENABLED=false
SPOTAI_VOUCHER_MQ_ENABLED=false
```

关闭 MQ 时，核心业务使用同步 fallback：

- 普通代金券领取会直接执行落库。
- 秒杀券抢单会在 Redis 原子校验后同步创建订单。
- 评论摘要刷新、UV、商户变更、笔记发布等事件会同步执行或保留已有主流程。

启用 RocketMQ 前，需要额外部署 NameServer/Broker，并配置：

```properties
SPOTAI_MQ_ENABLED=true
SPOTAI_VOUCHER_MQ_ENABLED=true
ROCKETMQ_NAME_SERVER=rocketmq-namesrv:9876
```

## 9. 更新与回滚

更新版本：

```bash
docker compose -f docker-compose.prod.yml --env-file .env pull
docker compose -f docker-compose.prod.yml --env-file .env up -d
```

回滚版本：

1. 把 `.env` 中的 `SPOTAI_BACKEND_IMAGE`、`SPOTAI_WEB_IMAGE` 改回旧 tag。
2. 重新执行 `docker compose up -d`。
3. 如涉及数据库变更，先确认是否需要使用备份回滚。

## 10. 安全建议

- 不要把 `.env` 提交到 Git。
- 生产安全组只开放 `80/443`。
- MySQL、Redis、MinIO Console 不建议公网开放。
- `MINIO_EXTERNAL_ENDPOINT` 必须是用户浏览器可访问的地址，否则图片无法显示。
- 公开部署前请轮换所有曾在本地或聊天中暴露过的密钥。
