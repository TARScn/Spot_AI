package com.tars.spotai.service;

import com.tars.spotai.entity.AiUserMemory;
import com.tars.spotai.repository.AiUserMemoryRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MysqlUserMemoryStoreTest {
    @Test
    void delegatesFindUpsertAndDeleteToRepository() {
        AiUserMemoryRepository repository = mock(AiUserMemoryRepository.class);
        MysqlUserMemoryStore store = new MysqlUserMemoryStore(repository);
        AiUserMemory memory = new AiUserMemory();
        memory.setMemoryKey("dining.preference.area");
        when(repository.findActiveByUserId(99L)).thenReturn(List.of(memory));
        when(repository.findActiveByUserIdAndNamespace(99L, "dining.preference")).thenReturn(List.of(memory));
        when(repository.findActiveByUserIdAndKey(99L, "dining.preference.area")).thenReturn(memory);

        assertThat(store.findActive(99L)).containsExactly(memory);
        assertThat(store.findActive(99L, "dining.preference")).containsExactly(memory);
        assertThat(store.findOne(99L, "dining.preference", "area")).contains(memory);
        store.upsert(1L, 99L, "dining.preference.area", "preference", "{\"area\":\"高新\"}", 0.8, 10L, "agent");
        store.delete(99L, "dining.preference", "area");
        store.delete(99L, "preferred_area");
        store.clear(99L);

        verify(repository).findActiveByUserId(99L);
        verify(repository).findActiveByUserIdAndNamespace(99L, "dining.preference");
        verify(repository).findActiveByUserIdAndKey(99L, "dining.preference.area");
        verify(repository).upsert(1L, 99L, "dining.preference.area", "dining.preference", "{\"area\":\"高新\"}", 0.8, 10L, "agent");
        verify(repository).markDeleted(99L, "dining.preference.area");
        verify(repository).markDeleted(99L, "preferred_area");
        verify(repository).markDeletedByUserId(99L);
    }
}
