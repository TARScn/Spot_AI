package com.tars.spotai.service;

import com.tars.spotai.entity.AiUserMemory;

import java.util.List;
import java.util.Optional;

public interface UserMemoryStore {
    List<AiUserMemory> findActive(Long userId);

    List<AiUserMemory> findActive(Long userId, String namespace);

    Optional<AiUserMemory> findOne(Long userId, String namespace, String key);

    void put(MemoryWriteCommand command);

    void delete(Long userId, String namespace, String key);

    void clear(Long userId);

    default void upsert(Long id, Long userId, String memoryKey, String memoryType, String memoryJson,
                        double confidence, Long sourceMessageId, String sourceAgent) {
        UserMemoryKey key = UserMemoryKey.fromLegacy(memoryKey, memoryType);
        put(new MemoryWriteCommand(
                id,
                userId,
                key.namespace(),
                key.key(),
                memoryJson,
                confidence,
                sourceMessageId,
                sourceAgent
        ));
    }

    default void delete(Long userId, String memoryKey) {
        UserMemoryKey key = UserMemoryKey.fromLegacy(memoryKey, UserMemoryKey.DEFAULT_NAMESPACE);
        delete(userId, key.namespace(), key.key());
    }

    record MemoryWriteCommand(Long id,
                              Long userId,
                              String namespace,
                              String key,
                              String memoryJson,
                              double confidence,
                              Long sourceMessageId,
                              String sourceAgent) {
        public MemoryWriteCommand {
            UserMemoryKey normalized = UserMemoryKey.of(namespace, key);
            namespace = normalized.namespace();
            key = normalized.key();
        }

        public String physicalKey() {
            return UserMemoryKey.of(namespace, key).physicalKey();
        }
    }
}
