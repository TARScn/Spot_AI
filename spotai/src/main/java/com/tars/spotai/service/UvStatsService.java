package com.tars.spotai.service;

import com.tars.spotai.config.MqEventProperties;
import com.tars.spotai.dto.Result;
import com.tars.spotai.dto.UserDTO;
import com.tars.spotai.dto.UvRecordDTO;
import com.tars.spotai.dto.UvRecordMessage;
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
    private final MqEventPublisher mqEventPublisher;
    private final MqEventProperties mqEventProperties;

    public UvStatsService(StringRedisTemplate stringRedisTemplate,
                          MqEventPublisher mqEventPublisher,
                          MqEventProperties mqEventProperties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.mqEventPublisher = mqEventPublisher;
        this.mqEventProperties = mqEventProperties;
    }

    public Result<Void> record(UvRecordDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getTargetType())) {
            return Result.fail("统计目标类型不能为空");
        }
        String visitor = resolveVisitor(dto.getVisitor());
        String today = LocalDate.now().format(DAY_FORMATTER);
        String key = targetKey(dto.getTargetType(), dto.getTargetId(), dto.getPageCode(), today);
        if (!StringUtils.hasText(key)) {
            return Result.fail("统计目标不合法");
        }

        UvRecordMessage message = new UvRecordMessage(
                dto.getTargetType(),
                dto.getTargetId(),
                dto.getPageCode(),
                visitor,
                today
        );
        mqEventPublisher.publishOrRun(
                mqEventProperties.getUvRecordTopic(),
                message,
                () -> recordDirect(message)
        );
        return Result.ok(null);
    }

    /**
     * MQ 消费端和本地兜底都会调用这里，真正写入 HyperLogLog。
     */
    public void recordDirect(UvRecordMessage message) {
        if (message == null
                || !StringUtils.hasText(message.getTargetType())
                || !StringUtils.hasText(message.getVisitor())) {
            return;
        }
        String day = StringUtils.hasText(message.getDay())
                ? message.getDay()
                : LocalDate.now().format(DAY_FORMATTER);
        String key = targetKey(message.getTargetType(), message.getTargetId(), message.getPageCode(), day);
        if (!StringUtils.hasText(key)) {
            return;
        }

        stringRedisTemplate.opsForHyperLogLog().add(RedisConstants.UV_SITE_KEY + day, message.getVisitor());
        if (!RedisConstants.UV_SITE_KEY.concat(day).equals(key)) {
            stringRedisTemplate.opsForHyperLogLog().add(key, message.getVisitor());
        }
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

    private String targetKey(String targetType, Long targetId, String pageCode, String day) {
        String normalizedType = targetType.trim().toLowerCase();
        return switch (normalizedType) {
            case "site" -> RedisConstants.UV_SITE_KEY + day;
            case "shop" -> targetId == null ? null : RedisConstants.UV_SHOP_KEY + targetId + ":" + day;
            case "blog" -> targetId == null ? null : RedisConstants.UV_BLOG_KEY + targetId + ":" + day;
            case "page" -> !StringUtils.hasText(pageCode) ? null : RedisConstants.UV_PAGE_KEY + pageCode + ":" + day;
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
