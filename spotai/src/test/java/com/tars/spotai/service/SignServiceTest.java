package com.tars.spotai.service;

import com.tars.spotai.dto.Result;
import com.tars.spotai.dto.UserDTO;
import com.tars.spotai.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SignServiceTest {
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private SignService signService;

    @BeforeEach
    void setUp() {
        signService = new SignService(stringRedisTemplate);
        UserHolder.saveUser(new UserDTO(1001L, "alice", ""));
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    void signWritesTodayBitWhenNotSigned() {
        when(valueOperations.setBit(anyString(), anyLong(), eq(true))).thenReturn(false);

        Result<Void> result = signService.sign();

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void signFailsWhenAlreadySignedToday() {
        when(valueOperations.setBit(anyString(), anyLong(), eq(true))).thenReturn(true);

        Result<Void> result = signService.sign();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMsg()).isEqualTo("今日已签到");
    }

    @Test
    void countContinuousSignDaysCountsTrailingOnes() {
        when(valueOperations.bitField(anyString(), any(BitFieldSubCommands.class))).thenReturn(List.of(0b111L));

        Result<Integer> result = signService.countContinuousSignDays();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(3);
    }
}
