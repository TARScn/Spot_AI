package com.tars.spotai.controller;

import com.tars.spotai.dto.Result;
import com.tars.spotai.dto.UvRecordDTO;
import com.tars.spotai.service.UvStatsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
public class StatsController {
    private final UvStatsService uvStatsService;

    public StatsController(UvStatsService uvStatsService) {
        this.uvStatsService = uvStatsService;
    }

    @PostMapping("/stats/uv")
    public Result<Void> recordUv(@RequestBody UvRecordDTO dto) {
        return uvStatsService.record(dto);
    }

    @GetMapping("/stats/uv/site")
    public Result<Long> siteUv(@RequestParam(required = false)
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return uvStatsService.siteUv(date);
    }

    @GetMapping("/stats/uv/shop/{shopId}")
    public Result<Long> shopUv(@PathVariable Long shopId,
                               @RequestParam(required = false)
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return uvStatsService.shopUv(shopId, date);
    }
}
