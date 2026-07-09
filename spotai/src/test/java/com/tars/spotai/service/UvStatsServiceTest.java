package com.tars.spotai.service;

import com.tars.spotai.config.MqEventProperties;
import com.tars.spotai.dto.Result;
import com.tars.spotai.dto.UserDTO;
import com.tars.spotai.dto.UvRecordDTO;
import com.tars.spotai.dto.UvRecordMessage;
import com.tars.spotai.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HyperLogLogOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UvStatsServiceTest {
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private HyperLogLogOperations<String, String> hyperLogLogOperations;
    @Mock
    private MqEventPublisher mqEventPublisher;

    private UvStatsService uvStatsService;

    @BeforeEach
    void setUp() {
        uvStatsService = new UvStatsService(stringRedisTemplate, mqEventPublisher, new MqEventProperties());
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    void recordPublishesUvEventWithCurrentUser() {
        UserHolder.saveUser(new UserDTO(1001L, "alice", ""));
        UvRecordDTO dto = new UvRecordDTO();
        dto.setTargetType("shop");
        dto.setTargetId(10L);

        Result<Void> result = uvStatsService.record(dto);

        assertThat(result.isSuccess()).isTrue();
        verify(mqEventPublisher).publishOrRun(
                eq("spotai.uv.record"),
                any(UvRecordMessage.class),
                any(Runnable.class)
        );
        verifyNoInteractions(stringRedisTemplate, hyperLogLogOperations);
    }

    @Test
    void recordDirectAddsSiteAndShopUv() {
        when(stringRedisTemplate.opsForHyperLogLog()).thenReturn(hyperLogLogOperations);
        UvRecordMessage message = new UvRecordMessage(
                "shop",
                10L,
                null,
                "user:1001",
                LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
        );

        uvStatsService.recordDirect(message);

        verify(hyperLogLogOperations).add(eq("uv:site:" + LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)), eq("user:1001"));
        verify(hyperLogLogOperations).add(eq("uv:shop:10:" + LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)), eq("user:1001"));
    }

    @Test
    void uvRecordConsumerDelegatesToDirectRecord() {
        UvStatsService service = org.mockito.Mockito.mock(UvStatsService.class);
        UvRecordConsumer consumer = new UvRecordConsumer(service);
        UvRecordMessage message = new UvRecordMessage("site", null, null, "visitor:1", "20260707");

        consumer.onMessage(message);

        verify(service).recordDirect(message);
    }

    @Test
    void recordFailsWhenTargetInvalid() {
        UvRecordDTO dto = new UvRecordDTO();
        dto.setTargetType("shop");

        Result<Void> result = uvStatsService.record(dto);

        assertThat(result.isSuccess()).isFalse();
        verifyNoInteractions(stringRedisTemplate, hyperLogLogOperations);
    }

    @Test
    void siteUvReturnsZeroWhenRedisReturnsNull() {
        when(stringRedisTemplate.opsForHyperLogLog()).thenReturn(hyperLogLogOperations);
        when(hyperLogLogOperations.size(any())).thenReturn(null);

        Result<Long> result = uvStatsService.siteUv(LocalDate.of(2026, 6, 11));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isZero();
        verify(hyperLogLogOperations).size("uv:site:20260611");
    }

    @Test
    void shopUvCountsByShopAndDate() {
        when(stringRedisTemplate.opsForHyperLogLog()).thenReturn(hyperLogLogOperations);
        when(hyperLogLogOperations.size("uv:shop:10:20260611")).thenReturn(88L);

        Result<Long> result = uvStatsService.shopUv(10L, LocalDate.of(2026, 6, 11));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(88L);
    }
}
