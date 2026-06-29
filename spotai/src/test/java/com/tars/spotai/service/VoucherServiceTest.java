package com.tars.spotai.service;

import com.tars.spotai.config.VoucherProperties;
import com.tars.spotai.dto.Result;
import com.tars.spotai.dto.UserDTO;
import com.tars.spotai.dto.VoucherOrderMessage;
import com.tars.spotai.entity.SeckillVoucher;
import com.tars.spotai.entity.Voucher;
import com.tars.spotai.repository.ShopRepository;
import com.tars.spotai.repository.VoucherRepository;
import com.tars.spotai.utils.RedisIdWorker;
import com.tars.spotai.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoucherServiceTest {
    @Mock
    private VoucherRepository voucherRepository;
    @Mock
    private ShopRepository shopRepository;
    @Mock
    private RedisIdWorker redisIdWorker;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private RocketMQTemplate rocketMQTemplate;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RLock lock;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private TransactionStatus transactionStatus;

    private VoucherService voucherService;

    @BeforeEach
    void setUp() {
        VoucherProperties voucherProperties = new VoucherProperties();
        voucherProperties.setOrderTopic("spotai.voucher-order.create");
        voucherService = new VoucherService(
                voucherRepository,
                shopRepository,
                redisIdWorker,
                stringRedisTemplate,
                rocketMQTemplate,
                voucherProperties,
                redissonClient,
                transactionTemplate
        );
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    void seckillVoucherSendsRocketMqMessageWhenRedisClaimSucceeds() {
        UserHolder.saveUser(new UserDTO(1001L, "user_1001", ""));
        when(voucherRepository.findVoucherById(2001L)).thenReturn(seckillVoucherBase());
        when(voucherRepository.findSeckillVoucherByVoucherId(2001L)).thenReturn(activeSeckillVoucher());
        when(stringRedisTemplate.hasKey("seckill:stock:2001")).thenReturn(true);
        when(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList(), eq("1001"))).thenReturn(0L);
        when(redisIdWorker.nextId("voucher_order")).thenReturn(9001L);

        Result<Long> result = voucherService.seckillVoucher(2001L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(9001L);
        verify(rocketMQTemplate).syncSend(eq("spotai.voucher-order.create"), any(VoucherOrderMessage.class), anyLong());
    }

    @Test
    void seckillVoucherCreatesOrderDirectlyWhenRocketMqSendFails() throws InterruptedException {
        UserHolder.saveUser(new UserDTO(1001L, "user_1001", ""));
        when(voucherRepository.findVoucherById(2001L)).thenReturn(seckillVoucherBase());
        when(voucherRepository.findSeckillVoucherByVoucherId(2001L)).thenReturn(activeSeckillVoucher());
        when(stringRedisTemplate.hasKey("seckill:stock:2001")).thenReturn(true);
        when(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList(), eq("1001")))
                .thenReturn(0L);
        when(redisIdWorker.nextId("voucher_order")).thenReturn(9001L);
        doThrow(new IllegalStateException("rocketmq down"))
                .when(rocketMQTemplate)
                .syncSend(eq("spotai.voucher-order.create"), any(VoucherOrderMessage.class), anyLong());

        mockSuccessfulDirectOrderCreation();

        Result<Long> result = voucherService.seckillVoucher(2001L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(9001L);
        verify(voucherRepository).insertVoucherOrder(9001L, 1001L, 2001L);
    }

    @Test
    void seckillVoucherCreatesOrderDirectlyWhenMqDisabled() throws InterruptedException {
        VoucherProperties voucherProperties = new VoucherProperties();
        voucherProperties.setMqEnabled(false);
        voucherService = new VoucherService(
                voucherRepository,
                shopRepository,
                redisIdWorker,
                stringRedisTemplate,
                rocketMQTemplate,
                voucherProperties,
                redissonClient,
                transactionTemplate
        );
        UserHolder.saveUser(new UserDTO(1001L, "user_1001", ""));
        when(voucherRepository.findVoucherById(2001L)).thenReturn(seckillVoucherBase());
        when(voucherRepository.findSeckillVoucherByVoucherId(2001L)).thenReturn(activeSeckillVoucher());
        when(stringRedisTemplate.hasKey("seckill:stock:2001")).thenReturn(true);
        when(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList(), eq("1001"))).thenReturn(0L);
        when(redisIdWorker.nextId("voucher_order")).thenReturn(9001L);
        mockSuccessfulDirectOrderCreation();

        Result<Long> result = voucherService.seckillVoucher(2001L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(9001L);
        verify(rocketMQTemplate, never()).syncSend(anyString(), any(VoucherOrderMessage.class), anyLong());
        verify(voucherRepository).insertVoucherOrder(9001L, 1001L, 2001L);
    }

    @Test
    void handleVoucherOrderCreatesOrderWithRedissonLockAndOptimisticStockDeduction() throws InterruptedException {
        VoucherOrderMessage message = new VoucherOrderMessage(
                9001L,
                1001L,
                2001L,
                9001L,
                LocalDateTime.now()
        );
        when(redissonClient.getLock("lock:order:1001:2001")).thenReturn(lock);
        when(lock.tryLock(1, 10, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(voucherRepository.existsOrder(1001L, 2001L)).thenReturn(false);
        when(voucherRepository.deductStock(2001L)).thenReturn(1);
        when(voucherRepository.queryStock(2001L)).thenReturn(9);
        when(redisIdWorker.nextId("voucher_order_router")).thenReturn(9101L);
        when(redisIdWorker.nextId("voucher_reconcile_log")).thenReturn(9201L);
        invokeTransactionCallback();

        voucherService.handleVoucherOrder(message);

        verify(voucherRepository).insertVoucherOrder(9001L, 1001L, 2001L);
        verify(voucherRepository).insertVoucherOrderRouter(9101L, 9001L, 1001L, 2001L);
        verify(voucherRepository).insertReconcileLog(9201L, 9001L, 1001L, 2001L, "9001", 10, -1, 9, 9001L);
        verify(lock).unlock();
        verify(stringRedisTemplate, never()).execute(any(DefaultRedisScript.class), anyList(), anyString());
    }

    private void invokeTransactionCallback() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(transactionStatus);
        });
    }

    private void mockSuccessfulDirectOrderCreation() throws InterruptedException {
        when(redissonClient.getLock("lock:order:1001:2001")).thenReturn(lock);
        when(lock.tryLock(1, 10, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(voucherRepository.existsOrder(1001L, 2001L)).thenReturn(false);
        when(voucherRepository.deductStock(2001L)).thenReturn(1);
        when(voucherRepository.queryStock(2001L)).thenReturn(9);
        when(redisIdWorker.nextId("voucher_order_router")).thenReturn(9101L);
        when(redisIdWorker.nextId("voucher_reconcile_log")).thenReturn(9201L);
        invokeTransactionCallback();
    }

    private Voucher seckillVoucherBase() {
        Voucher voucher = new Voucher();
        voucher.setId(2001L);
        voucher.setType(1);
        voucher.setStatus(1);
        return voucher;
    }

    private SeckillVoucher activeSeckillVoucher() {
        SeckillVoucher voucher = new SeckillVoucher();
        voucher.setVoucherId(2001L);
        voucher.setBeginTime(LocalDateTime.now().minusMinutes(1));
        voucher.setEndTime(LocalDateTime.now().plusMinutes(10));
        return voucher;
    }

}
