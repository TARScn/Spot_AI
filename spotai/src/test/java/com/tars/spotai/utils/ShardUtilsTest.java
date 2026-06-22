package com.tars.spotai.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShardUtilsTest {
    @Test
    void routesEmailToKnownUserAndEmailTables() {
        String email = "spotai@qq.com";
        int shard = ShardUtils.emailShard(email);

        assertThat(shard).isBetween(0, 1);
        assertThat(ShardUtils.userTable(email)).isEqualTo("tb_user_" + shard);
        assertThat(ShardUtils.userEmailTable(email)).isEqualTo("tb_user_email_" + shard);
    }
}
