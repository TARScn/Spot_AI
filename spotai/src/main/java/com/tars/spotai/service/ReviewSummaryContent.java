package com.tars.spotai.service;

import java.util.List;

public record ReviewSummaryContent(
        String summary,
        List<String> highlights,
        List<String> weaknesses,
        List<String> scenes
) {
}
