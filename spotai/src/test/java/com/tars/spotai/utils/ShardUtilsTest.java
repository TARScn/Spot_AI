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

    @Test
    void routesAiTablesByUserId() {
        Long userId = 1001L;
        int shard = ShardUtils.idShard(userId);

        assertThat(ShardUtils.aiConversationTable(userId)).isEqualTo("tb_ai_conversation_" + shard);
        assertThat(ShardUtils.aiUserMemoryTable(userId)).isEqualTo("tb_ai_user_memory_" + shard);
        assertThat(ShardUtils.aiToolCallLogTable(userId)).isEqualTo("tb_ai_tool_call_log_" + shard);
    }
}
