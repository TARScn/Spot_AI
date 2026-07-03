package com.tars.spotai.service;

import com.tars.spotai.dto.Result;
import com.tars.spotai.dto.UserDTO;
import com.tars.spotai.utils.RedisConstants;
import com.tars.spotai.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class SignService {
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    private final StringRedisTemplate stringRedisTemplate;
    private final Clock clock;

    @Autowired
    public SignService(StringRedisTemplate stringRedisTemplate) {
        this(stringRedisTemplate, Clock.systemDefaultZone());
    }

    SignService(StringRedisTemplate stringRedisTemplate, Clock clock) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.clock = clock;
    }

    public Result<Void> sign() {
        UserDTO user = UserHolder.getUser();
        if (user == null || user.getId() == null) {
            return Result.fail("请先登录");
        }
        LocalDate now = LocalDate.now(clock);
        String key = signKey(user.getId(), now);
        int offset = now.getDayOfMonth() - 1;
        Boolean alreadySigned = stringRedisTemplate.opsForValue().setBit(key, offset, true);
        if (Boolean.TRUE.equals(alreadySigned)) {
            return Result.fail("今日已签到");
        }
        return Result.ok(null);
    }

    public Result<Integer> countContinuousSignDays() {
        UserDTO user = UserHolder.getUser();
        if (user == null || user.getId() == null) {
            return Result.fail("请先登录");
        }
        LocalDate now = LocalDate.now(clock);
        int dayOfMonth = now.getDayOfMonth();
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                signKey(user.getId(), now),
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                        .valueAt(0)
        );
        if (result == null || result.isEmpty() || result.get(0) == null) {
            return Result.ok(0);
        }
        long num = result.get(0);
        int count = 0;
        for (int i = 0; i < dayOfMonth; i++) {
            if ((num & 1) == 0) {
                break;
            }
            count++;
            num >>>= 1;
        }
        return Result.ok(count);
    }

    private String signKey(Long userId, LocalDate date) {
        return RedisConstants.USER_SIGN_KEY + userId + ":" + date.format(MONTH_FORMATTER);
    }
}
