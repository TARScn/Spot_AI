package com.tars.spotai.utils;

/**
 * Centralized constants for Redis key prefixes used across the application.
 */
public final class RedisConstants {
    /* ---- Key 前缀 ---- */
    /** 1. 登录验证码缓存 key（后缀：手机号） */
    public static final String LOGIN_CODE_KEY = "login:code:";

    /** 2. 登录 Token 缓存 key（后缀：token 字符串） */
    public static final String LOGIN_TOKEN_KEY = "login:token:";

    /** 3. 商户详情缓存 key（后缀：商户 ID） */
    public static final String CACHE_SHOP_KEY = "cache:shop:";

    /** 4. 秒杀券 Redis 库存 key（后缀：优惠券 ID） */
    public static final String SECKILL_STOCK_KEY = "seckill:stock:";

    /** 5. 秒杀券已下单用户集合 key（后缀：优惠券 ID） */
    public static final String SECKILL_ORDER_KEY = "seckill:order:";

    /* ---- TTL 配置 ---- */
    /** 6. 商户缓存 TTL（分钟） */
    public static final long CACHE_SHOP_TTL_MINUTES = 30L;

    /** 7. 缓存随机加成 TTL 上限（秒），用于防缓存雪崩 */
    public static final long CACHE_RANDOM_TTL_SECONDS = 300L;

    /* ---- 缓存重建锁 ---- */
    /** 8. 商户缓存重建锁 key（后缀：资源 ID） */
    public static final String LOCK_SHOP_KEY = "lock:shop:";

    /** 9. 商户缓存重建锁 TTL（秒） */
    public static final long LOCK_SHOP_TTL_SECONDS = 10L;

    /** 10. 缓存重建锁默认 TTL（秒） */
    public static final long CACHE_REBUILD_LOCK_TTL_SECONDS = 10L;

    /** 11. 空值缓存 TTL（分钟），用于防缓存穿透 */
    public static final long CACHE_NULL_TTL_MINUTES = 2L;

    /** 12. 秒杀订单用户维度锁 key（后缀：userId:voucherId） */
    public static final String LOCK_VOUCHER_ORDER_KEY = "lock:order:";

    /** 13. 探店笔记点赞 ZSet key（后缀：blogId） */
    public static final String BLOG_LIKED_KEY = "blog:liked:";

    /** 14. 用户关注集合 key（后缀：userId） */
    public static final String FOLLOW_KEY = "follows:";

    /** 15. 用户粉丝集合 key（后缀：userId） */
    public static final String FOLLOWERS_KEY = "followers:";

    /** 16. 用户关注 Feed 收件箱 key（后缀：userId） */
    public static final String FEED_KEY = "feed:user:";

    /** 17. 商户 GEO key（后缀：typeId） */
    public static final String SHOP_GEO_KEY = "shop:geo:";

    /** 18. 用户签到 BitMap key（后缀：userId:yyyyMM） */
    public static final String USER_SIGN_KEY = "sign:";

    /** 19. 全站 UV HyperLogLog key（后缀：yyyyMMdd） */
    public static final String UV_SITE_KEY = "uv:site:";

    /** 20. 商户 UV HyperLogLog key（后缀：shopId:yyyyMMdd） */
    public static final String UV_SHOP_KEY = "uv:shop:";

    /** 21. 探店笔记 UV HyperLogLog key（后缀：blogId:yyyyMMdd） */
    public static final String UV_BLOG_KEY = "uv:blog:";

    /** 22. 页面 UV HyperLogLog key（后缀：pageCode:yyyyMMdd） */
    public static final String UV_PAGE_KEY = "uv:page:";

    private RedisConstants() {
    }
}
