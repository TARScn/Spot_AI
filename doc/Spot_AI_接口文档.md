# Spot AI 接口文档

> 核对日期：2026-07-14  
> 核对范围：`spotai/src/main/java/com/tars/spotai/controller`。

## 1. 通用约定

基础地址：

```text
http://localhost:8080
```

统一响应：

```json
{
  "success": true,
  "data": {},
  "errorMsg": null
}
```

需要登录的接口通过请求头传入 token：

```http
Authorization: {token}
```

## 2. 用户接口

| 方法 | 路径 | 登录 | 说明 |
| --- | --- | --- | --- |
| `POST` | `/user/code?email={email}` | 否 | 发送邮箱验证码 |
| `POST` | `/user/login` | 否 | 邮箱验证码登录，用户不存在时自动注册 |
| `GET` | `/user/me` | 是 | 当前登录用户 |
| `GET` | `/user/profile` | 是 | 我的页面聚合数据 |
| `POST` | `/user/sign` | 是 | 每日签到 |
| `GET` | `/user/sign/count` | 是 | 查询当月连续签到天数 |

登录请求体：

```json
{
  "email": "user@example.com",
  "code": "123456"
}
```

## 3. 店铺接口

| 方法 | 路径 | 登录 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/shop/{id}` | 否 | 店铺详情 |
| `GET` | `/shop/{id}/items` | 否 | 店铺菜品/服务 |
| `GET` | `/shop/of/type` | 否 | 按分类分页查询；传 `x/y` 时支持附近查询 |
| `GET` | `/shop/search` | 否 | 店铺搜索 |
| `PUT` | `/shop` | 否 | 更新店铺信息 |
| `PUT` | `/shop/geo/load` | 否 | 店铺 GEO 预热 |
| `GET` | `/shop-type/list` | 否 | 店铺分类 |

## 4. 探店笔记接口

| 方法 | 路径 | 登录 | 说明 |
| --- | --- | --- | --- |
| `POST` | `/blog` | 是 | 发布笔记 |
| `GET` | `/blog/hot` | 否 | 热门笔记 |
| `GET` | `/blog/recent` | 否 | 最近笔记 |
| `GET` | `/blog/of/me` | 是 | 我的笔记 |
| `GET` | `/blog/liked/me` | 是 | 我点赞过的笔记 |
| `GET` | `/blog/of/user` | 否 | 指定用户笔记 |
| `GET` | `/blog/of/shop` | 否 | 指定店铺笔记 |
| `GET` | `/blog/of/follow` | 是 | 关注流 |
| `GET` | `/blog/{id}` | 否 | 笔记详情 |
| `PUT` | `/blog/like/{id}` | 是 | 点赞或取消点赞 |
| `DELETE` | `/blog/{id}` | 是 | 删除自己的笔记 |
| `GET` | `/blog/likes/{id}` | 否 | 点赞用户列表 |

## 5. 关注接口

| 方法 | 路径 | 登录 | 说明 |
| --- | --- | --- | --- |
| `PUT` | `/follow/{id}/{isFollow}` | 是 | 关注或取消关注 |
| `GET` | `/follow/or/not/{id}` | 是 | 判断是否关注 |
| `GET` | `/follow/common/{id}` | 是 | 查询共同关注 |

## 6. 评价接口

| 方法 | 路径 | 登录 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/review/of/shop?shopId={id}&current=1` | 否 | 店铺评价分页 |
| `GET` | `/review/of/me` | 是 | 我的评价 |
| `POST` | `/review` | 是 | 发布店铺评价 |
| `DELETE` | `/review/{id}` | 是 | 删除自己的评价 |
| `GET` | `/review/summary?shopId={id}` | 否 | 店铺评论 AI 摘要 |

## 7. 优惠券接口

| 方法 | 路径 | 登录 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/voucher/activities` | 否 | 可用活动列表 |
| `GET` | `/voucher/activities/of/shop?shopId={id}` | 否 | 店铺可用优惠 |
| `POST` | `/voucher` | 否 | 新增普通券 |
| `POST` | `/voucher/seckill` | 否 | 新增秒杀券 |
| `POST` | `/voucher-order/{voucherId}` | 是 | 领取普通代金券 |
| `POST` | `/voucher-order/seckill/{voucherId}` | 是 | 秒杀券下单 |

## 8. 上传接口

| 方法 | 路径 | 登录 | 说明 |
| --- | --- | --- | --- |
| `POST` | `/upload/file` | 是 | 通用文件上传 |
| `POST` | `/upload/blog` | 是 | 探店笔记图片上传 |
| `DELETE` | `/upload/file?url={url}` | 是 | 删除文件 |

请求类型为 `multipart/form-data`，字段名为 `file`。

## 9. UV 统计接口

| 方法 | 路径 | 登录 | 说明 |
| --- | --- | --- | --- |
| `POST` | `/stats/uv` | 否 | 记录 UV |
| `GET` | `/stats/uv/site?date=yyyy-MM-dd` | 否 | 查询全站 UV |
| `GET` | `/stats/uv/shop/{shopId}?date=yyyy-MM-dd` | 否 | 查询店铺 UV |

## 10. AI 接口

| 方法 | 路径 | 登录 | 说明 |
| --- | --- | --- | --- |
| `POST` | `/ai/chat` | 可匿名 | AI 对话 |
| `GET` | `/ai/conversations/recent` | 是 | 最近对话 |
| `DELETE` | `/ai/conversations` | 是 | 清空对话 |
| `GET` | `/ai/memories` | 是 | AI 长期记忆 |
| `DELETE` | `/ai/memories` | 是 | 清空 AI 记忆 |
| `DELETE` | `/ai/memories/{memoryKey}` | 是 | 删除单条记忆 |
| `POST` | `/ai/tool/confirm` | 是 | 确认 AI 中风险工具调用 |

## 11. 管理接口

| 方法 | 路径 | 登录 | 说明 |
| --- | --- | --- | --- |
| `POST` | `/admin/review/summary/reindex` | 管理 Key | 重建评论摘要索引/摘要 |

管理接口使用 `ReviewSummaryAdminController`，需要配置 `REVIEW_AI_ADMIN_KEY`。

## 12. 当前未实现

- `/user/logout` 后端接口未提供，前端退出主要删除本地 token。
- 笔记评论接口未实现。
- 独立支付和退款接口未实现。
