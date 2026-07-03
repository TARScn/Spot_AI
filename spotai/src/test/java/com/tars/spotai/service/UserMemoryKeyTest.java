package com.tars.spotai.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserMemoryKeyTest {
    @Test
    void splitsLegacyDottedKeyIntoNamespaceAndKey() {
        UserMemoryKey key = UserMemoryKey.fromLegacy("dining.preference.area", "preference");

        assertThat(key.namespace()).isEqualTo("dining.preference");
        assertThat(key.key()).isEqualTo("area");
        assertThat(key.physicalKey()).isEqualTo("dining.preference.area");
    }

    @Test
    void usesMemoryTypeAsNamespaceForPlainLegacyKey() {
        UserMemoryKey key = UserMemoryKey.fromLegacy("preferred_area", "preference");

        assertThat(key.namespace()).isEqualTo("preference");
        assertThat(key.key()).isEqualTo("preferred_area");
        assertThat(key.physicalKey()).isEqualTo("preference.preferred_area");
    }
}
