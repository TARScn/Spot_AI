package com.tars.spotai.service;

import com.tars.spotai.entity.AiUserMemory;
import com.tars.spotai.repository.AiUserMemoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class MysqlUserMemoryStore implements UserMemoryStore {
    private final AiUserMemoryRepository memoryRepository;

    public MysqlUserMemoryStore(AiUserMemoryRepository memoryRepository) {
        this.memoryRepository = memoryRepository;
    }

    @Override
    public List<AiUserMemory> findActive(Long userId) {
        return memoryRepository.findActiveByUserId(userId);
    }

    @Override
    public List<AiUserMemory> findActive(Long userId, String namespace) {
        UserMemoryKey key = UserMemoryKey.of(namespace, "_");
        return memoryRepository.findActiveByUserIdAndNamespace(userId, key.namespace());
    }

    @Override
    public Optional<AiUserMemory> findOne(Long userId, String namespace, String key) {
        UserMemoryKey memoryKey = UserMemoryKey.of(namespace, key);
        return Optional.ofNullable(memoryRepository.findActiveByUserIdAndKey(userId, memoryKey.physicalKey()));
    }

    @Override
    public void put(MemoryWriteCommand command) {
        memoryRepository.upsert(
                command.id(),
                command.userId(),
                command.physicalKey(),
                command.namespace(),
                command.memoryJson(),
                command.confidence(),
                command.sourceMessageId(),
                command.sourceAgent());
    }

    @Override
    public void delete(Long userId, String namespace, String key) {
        memoryRepository.markDeleted(userId, UserMemoryKey.of(namespace, key).physicalKey());
    }

    @Override
    public void delete(Long userId, String memoryKey) {
        memoryRepository.markDeleted(userId, memoryKey);
    }

    @Override
    public void clear(Long userId) {
        memoryRepository.markDeletedByUserId(userId);
    }
}
