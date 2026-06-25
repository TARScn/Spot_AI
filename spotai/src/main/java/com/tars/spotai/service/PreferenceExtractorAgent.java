package com.tars.spotai.service;

import com.tars.spotai.dto.PreferenceMemoryCandidateDTO;
import com.tars.spotai.entity.AiUserMemory;

import java.util.List;

public interface PreferenceExtractorAgent {
    String AGENT_NAME = "PreferenceExtractorAgent";

    List<PreferenceMemoryCandidateDTO> extract(Long userId, String latestUserMessage, List<AiUserMemory> existingMemories);
}
