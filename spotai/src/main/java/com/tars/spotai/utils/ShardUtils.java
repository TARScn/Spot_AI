package com.tars.spotai.utils;

/**
 * Utility for database sharding based on phone number.
 * Provides consistent table name resolution across the application.
 */
public final class ShardUtils {
    private ShardUtils() {
    }

    /* 1. 根据手机号计算分片索引（0 或 1） */
    public static int phoneShard(String phone) {
        return Math.floorMod(phone.hashCode(), 2);
    }

    /* 2. 获取用户表表名 */
    public static String userTable(String phone) {
        return "tb_user_" + phoneShard(phone);
    }

    /* 3. 获取手机号索引表表名 */
    public static String userPhoneTable(String phone) {
        return "tb_user_phone_" + phoneShard(phone);
    }

    /* 4. 根据 Long ID 计算分片索引（0 或 1） */
    public static int idShard(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        return Math.floorMod(id.hashCode(), 2);
    }

    /* 5. 获取优惠券主表表名 */
    public static String voucherTable(Long voucherId) {
        return "tb_voucher_" + idShard(voucherId);
    }

    /* 6. 获取秒杀券表表名 */
    public static String seckillVoucherTable(Long voucherId) {
        return "tb_seckill_voucher_" + idShard(voucherId);
    }

    /* 7. 获取订单表表名（按 userId 路由，便于查询用户订单） */
    public static String voucherOrderTable(Long userId) {
        return "tb_voucher_order_" + idShard(userId);
    }

    /* 8. 获取订单路由表表名（按 orderId 路由） */
    public static String voucherOrderRouterTable(Long orderId) {
        return "tb_voucher_order_router_" + idShard(orderId);
    }

    /* 9. 获取对账日志表表名（按 orderId 路由） */
    public static String voucherReconcileLogTable(Long orderId) {
        return "tb_voucher_reconcile_log_" + idShard(orderId);
    }
}
