package com.tars.spotai.service;

import com.tars.spotai.entity.AiUserMemory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class AgentMemorySelectionPolicy {
    private static final String ROUTE_SHOP = "SHOP_GUIDE";
    private static final String ROUTE_REVIEW = "REVIEW_RAG";
    private static final String ROUTE_COUPON = "COUPON";
    private static final String ROUTE_ORDER = "ORDER_GUARD";

    public List<AiUserMemory> selectForPrompt(String agentRoute, List<AiUserMemory> memories) {
        if (memories == null || memories.isEmpty()) {
            return List.of();
        }
        Set<String> allowedNamespaces = allowedNamespaces(agentRoute);
        Map<String, AiUserMemory> selected = new LinkedHashMap<>();
        for (AiUserMemory memory : memories) {
            if (memory == null || !StringUtils.hasText(memory.getMemoryJson()) || !allowedNamespaces.contains(namespaceOf(memory))) {
                continue;
            }
            String identity = identityOf(memory);
            if (!StringUtils.hasText(identity)) {
                continue;
            }
            if (selected.containsKey(identity)) {
                selected.remove(identity);
            }
            selected.put(identity, memory);
        }
        return new ArrayList<>(selected.values());
    }

    public Set<String> allowedNamespaces(String agentRoute) {
        Set<String> namespaces = new LinkedHashSet<>();
        if (ROUTE_SHOP.equals(agentRoute)) {
            namespaces.add("dining.preference");
            namespaces.add("preference");
            namespaces.add("conversation.summary");
            return namespaces;
        }
        if (ROUTE_REVIEW.equals(agentRoute)) {
            namespaces.add("dining.preference");
            namespaces.add("review.preference");
            namespaces.add("preference");
            namespaces.add("conversation.summary");
            return namespaces;
        }
        if (ROUTE_COUPON.equals(agentRoute) || ROUTE_ORDER.equals(agentRoute)) {
            namespaces.add("coupon.preference");
            namespaces.add("dining.preference");
            namespaces.add("preference");
            namespaces.add("conversation.summary");
            return namespaces;
        }
        namespaces.add("profile");
        namespaces.add("preference");
        namespaces.add("conversation.summary");
        return namespaces;
    }

    public String namespaceOf(AiUserMemory memory) {
        try {
            return UserMemoryKey.fromLegacy(memory.getMemoryKey(), memory.getMemoryType()).namespace();
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    public String identityOf(AiUserMemory memory) {
        if (memory == null) {
            return "";
        }
        try {
            return UserMemoryKey.fromLegacy(memory.getMemoryKey(), memory.getMemoryType()).physicalKey();
        } catch (IllegalArgumentException ignored) {
            return memory.getMemoryKey() == null ? "" : memory.getMemoryKey();
        }
    }
}
