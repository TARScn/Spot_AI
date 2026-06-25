package com.tars.spotai.utils;

/**
 * Utility for resolving sharded table names.
 */
public final class ShardUtils {
    private ShardUtils() {
    }

    public static int emailShard(String email) {
        return Math.floorMod(email.hashCode(), 2);
    }

    public static String userTable(String email) {
        return "tb_user_" + emailShard(email);
    }

    public static String userEmailTable(String email) {
        return "tb_user_email_" + emailShard(email);
    }

    public static int idShard(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        return Math.floorMod(id.hashCode(), 2);
    }

    public static String voucherTable(Long voucherId) {
        return "tb_voucher_" + idShard(voucherId);
    }

    public static String seckillVoucherTable(Long voucherId) {
        return "tb_seckill_voucher_" + idShard(voucherId);
    }

    public static String voucherOrderTable(Long userId) {
        return "tb_voucher_order_" + idShard(userId);
    }

    public static String voucherOrderRouterTable(Long orderId) {
        return "tb_voucher_order_router_" + idShard(orderId);
    }

    public static String voucherReconcileLogTable(Long orderId) {
        return "tb_voucher_reconcile_log_" + idShard(orderId);
    }

    public static String aiConversationTable(Long userId) {
        return "tb_ai_conversation_" + idShard(userId);
    }

    public static String aiUserMemoryTable(Long userId) {
        return "tb_ai_user_memory_" + idShard(userId);
    }
}
