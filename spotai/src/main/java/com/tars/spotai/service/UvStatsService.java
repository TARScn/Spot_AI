package com.tars.spotai.service;

import com.tars.spotai.dto.Result;
import com.tars.spotai.dto.UserDTO;
import com.tars.spotai.dto.UvRecordDTO;
import com.tars.spotai.utils.RedisConstants;
import com.tars.spotai.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
public class UvStatsService {
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final StringRedisTemplate stringRedisTemplate;

    public UvStatsService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public Result<Void> record(UvRecordDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getTargetType())) {
            return Result.fail("统计目标类型不能为空");
        }
        String visitor = resolveVisitor(dto.getVisitor());
        String today = LocalDate.now().format(DAY_FORMATTER);
        String key = targetKey(dto, today);
        if (!StringUtils.hasText(key)) {
            return Result.fail("统计目标不合法");
        }
        stringRedisTemplate.opsForHyperLogLog().add(RedisConstants.UV_SITE_KEY + today, visitor);
        if (RedisConstants.UV_SITE_KEY.concat(today).equals(key)) {
            return Result.ok(null);
        }
        stringRedisTemplate.opsForHyperLogLog().add(key, visitor);
        return Result.ok(null);
    }

    public Result<Long> siteUv(LocalDate date) {
        return Result.ok(count(RedisConstants.UV_SITE_KEY + formatDate(date)));
    }

    public Result<Long> shopUv(Long shopId, LocalDate date) {
        if (shopId == null || shopId <= 0) {
            return Result.fail("商户ID不合法");
        }
        return Result.ok(count(RedisConstants.UV_SHOP_KEY + shopId + ":" + formatDate(date)));
    }

    private Long count(String key) {
        Long size = stringRedisTemplate.opsForHyperLogLog().size(key);
        return size == null ? 0L : size;
    }

    private String targetKey(UvRecordDTO dto, String day) {
        String targetType = dto.getTargetType().trim().toLowerCase();
        return switch (targetType) {
            case "site" -> RedisConstants.UV_SITE_KEY + day;
            case "shop" -> dto.getTargetId() == null ? null : RedisConstants.UV_SHOP_KEY + dto.getTargetId() + ":" + day;
            case "blog" -> dto.getTargetId() == null ? null : RedisConstants.UV_BLOG_KEY + dto.getTargetId() + ":" + day;
            case "page" -> !StringUtils.hasText(dto.getPageCode()) ? null : RedisConstants.UV_PAGE_KEY + dto.getPageCode() + ":" + day;
            default -> null;
        };
    }

    private String resolveVisitor(String visitor) {
        if (StringUtils.hasText(visitor)) {
            return visitor.trim();
        }
        UserDTO user = UserHolder.getUser();
        if (user != null && user.getId() != null) {
            return "user:" + user.getId();
        }
        return "visitor:anonymous";
    }

    private String formatDate(LocalDate date) {
        LocalDate value = date == null ? LocalDate.now() : date;
        return value.format(DAY_FORMATTER);
    }
}
