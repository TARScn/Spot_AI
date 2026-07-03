package com.tars.spotai.service;

import com.tars.spotai.entity.AiUserMemory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentMemorySelectionPolicyTest {
    private final AgentMemorySelectionPolicy policy = new AgentMemorySelectionPolicy();

    @Test
    void shopRouteAllowsDiningPreferenceAndLegacyPreferenceOnly() {
        assertThat(policy.allowedNamespaces("SHOP_GUIDE"))
                .containsExactly("dining.preference", "preference", "conversation.summary");
    }

    @Test
    void couponRouteAllowsCouponDiningAndLegacyPreference() {
        assertThat(policy.allowedNamespaces("COUPON"))
                .containsExactly("coupon.preference", "dining.preference", "preference", "conversation.summary");
    }

    @Test
    void filtersMemoriesByRouteNamespaceAndContent() {
        AiUserMemory dining = memory(1L, "dining.preference.area", "dining.preference", "{\"area\":\"高新\"}");
        AiUserMemory legacy = memory(2L, "preferred_area", "preference", "{\"area\":\"钟楼\"}");
        AiUserMemory profile = memory(3L, "profile.private", "profile", "{\"name\":\"alice\"}");
        AiUserMemory empty = memory(4L, "dining.preference.empty", "dining.preference", "");
        AiUserMemory summary = memory(5L, "conversation.summary.default", "conversation.summary", "{\"summary\":\"喜欢安静店\"}");

        List<AiUserMemory> selected = policy.selectForPrompt("SHOP_GUIDE", List.of(dining, legacy, profile, empty, summary));

        assertThat(selected).containsExactly(dining, legacy, summary);
    }

    @Test
    void keepsOnlyLatestMemoryForSameNormalizedKey() {
        AiUserMemory oldArea = memory(1L, "dining.preference.area", "dining.preference", "{\"area\":\"高新\"}");
        AiUserMemory newArea = memory(2L, "dining.preference.area", "dining.preference", "{\"area\":\"小寨\"}");
        AiUserMemory budget = memory(3L, "dining.preference.budget", "dining.preference", "{\"min\":40,\"max\":60}");

        List<AiUserMemory> selected = policy.selectForPrompt("SHOP_GUIDE", List.of(oldArea, budget, newArea));

        assertThat(selected).containsExactly(budget, newArea);
    }

    @Test
    void identityUsesNormalizedMemoryKeyInsteadOfStorageId() {
        AiUserMemory withId = memory(99L, "dining.preference.area", "dining.preference", "{}");
        AiUserMemory withoutId = memory(null, "dining.preference.budget", "dining.preference", "{}");
        AiUserMemory legacy = memory(100L, "preferred_area", "preference", "{}");

        assertThat(policy.identityOf(withId)).isEqualTo("dining.preference.area");
        assertThat(policy.identityOf(withoutId)).isEqualTo("dining.preference.budget");
        assertThat(policy.identityOf(legacy)).isEqualTo("preference.preferred_area");
    }

    private static AiUserMemory memory(Long id, String key, String type, String json) {
        AiUserMemory memory = new AiUserMemory();
        memory.setId(id);
        memory.setMemoryKey(key);
        memory.setMemoryType(type);
        memory.setMemoryJson(json);
        return memory;
    }
}
