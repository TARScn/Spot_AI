package com.tars.spotai.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ShardUtils} — verifies that phone-based sharding produces
 * consistent table names within the valid shard range.
 */
class ShardUtilsTest {

    /* 验证手机号分片路由在合法范围内且表名一致 */
    @Test
    void routesPhoneToKnownUserAndPhoneTables() {
        String phone = "13800138000";
        int shard = ShardUtils.phoneShard(phone);

        assertThat(shard).isBetween(0, 1);
        assertThat(ShardUtils.userTable(phone)).isEqualTo("tb_user_" + shard);
        assertThat(ShardUtils.userPhoneTable(phone)).isEqualTo("tb_user_phone_" + shard);
    }
}
