package com.tars.spotai.service;

import com.tars.spotai.config.VoucherProperties;
import com.tars.spotai.dto.Result;
import com.tars.spotai.dto.SeckillVoucherDTO;
import com.tars.spotai.dto.UserDTO;
import com.tars.spotai.dto.VoucherDTO;
import com.tars.spotai.dto.VoucherOrderMessage;
import com.tars.spotai.entity.SeckillVoucher;
import com.tars.spotai.entity.Voucher;
import com.tars.spotai.repository.ShopRepository;
import com.tars.spotai.repository.VoucherRepository;
import com.tars.spotai.utils.RedisConstants;
import com.tars.spotai.utils.RedisIdWorker;
import com.tars.spotai.utils.UserHolder;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class VoucherService {
    private static final int VOUCHER_TYPE_NORMAL = 0;
    private static final int VOUCHER_TYPE_SECKILL = 1;
    private static final int VOUCHER_STATUS_ON_SHELF = 1;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT = new DefaultRedisScript<>(
            """
                    if tonumber(redis.call('get', KEYS[1]) or '0') <= 0 then
                      return 1
                    end

                    if redis.call('sismember', KEYS[2], ARGV[1]) == 1 then
                      return 2
                    end

                    redis.call('decr', KEYS[1])
                    redis.call('sadd', KEYS[2], ARGV[1])
                    return 0
                    """,
            Long.class
    );

    private static final DefaultRedisScript<Long> ROLLBACK_SCRIPT = new DefaultRedisScript<>(
            """
                    redis.call('incr', KEYS[1])
                    redis.call('srem', KEYS[2], ARGV[1])
                    return 0
                    """,
            Long.class
    );

    private final VoucherRepository voucherRepository;
    private final ShopRepository shopRepository;
    private final RedisIdWorker redisIdWorker;
    private final StringRedisTemplate stringRedisTemplate;
    private final RocketMQTemplate rocketMQTemplate;
    private final VoucherProperties voucherProperties;
    private final RedissonClient redissonClient;
    private final TransactionTemplate transactionTemplate;

    public VoucherService(VoucherRepository voucherRepository,
                          ShopRepository shopRepository,
                          RedisIdWorker redisIdWorker,
                          StringRedisTemplate stringRedisTemplate,
                          RocketMQTemplate rocketMQTemplate,
                          VoucherProperties voucherProperties,
                          RedissonClient redissonClient,
                          TransactionTemplate transactionTemplate) {
        this.voucherRepository = voucherRepository;
        this.shopRepository = shopRepository;
        this.redisIdWorker = redisIdWorker;
        this.stringRedisTemplate = stringRedisTemplate;
        this.rocketMQTemplate = rocketMQTemplate;
        this.voucherProperties = voucherProperties;
        this.redissonClient = redissonClient;
        this.transactionTemplate = transactionTemplate;
    }

    public Result<Long> addVoucher(VoucherDTO dto) {
        Result<Void> validation = validateVoucher(dto);
        if (!validation.isSuccess()) {
            return Result.fail(validation.getErrorMsg());
        }

        Long voucherId = redisIdWorker.nextId("voucher");
        voucherRepository.insertVoucher(voucherId, dto, VOUCHER_TYPE_NORMAL);
        return Result.ok(voucherId);
    }

    public Result<Long> addSeckillVoucher(SeckillVoucherDTO dto) {
        Result<Void> validation = validateVoucher(dto);
        if (!validation.isSuccess()) {
            return Result.fail(validation.getErrorMsg());
        }
        if (!dto.getBeginTime().isBefore(dto.getEndTime())) {
            return Result.fail("秒杀开始时间必须早于结束时间");
        }

        Long voucherId = redisIdWorker.nextId("voucher");
        Long seckillVoucherId = redisIdWorker.nextId("seckill_voucher");

        transactionTemplate.executeWithoutResult(status -> {
            voucherRepository.insertVoucher(voucherId, dto, VOUCHER_TYPE_SECKILL);
            voucherRepository.insertSeckillVoucher(seckillVoucherId, voucherId, dto);
        });

        stringRedisTemplate.opsForValue()
                .set(RedisConstants.SECKILL_STOCK_KEY + voucherId, dto.getStock().toString());
        stringRedisTemplate.delete(RedisConstants.SECKILL_ORDER_KEY + voucherId);
        return Result.ok(voucherId);
    }

    public Result<Long> seckillVoucher(Long voucherId) {
        UserDTO user = UserHolder.getUser();
        if (user == null || user.getId() == null) {
            return Result.fail("请先登录");
        }
        Result<Void> validation = validateSeckillRequest(voucherId);
        if (!validation.isSuccess()) {
            return Result.fail(validation.getErrorMsg());
        }

        Long userId = user.getId();
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                List.of(RedisConstants.SECKILL_STOCK_KEY + voucherId, RedisConstants.SECKILL_ORDER_KEY + voucherId),
                userId.toString()
        );
        if (Long.valueOf(1).equals(result)) {
            return Result.fail("库存不足");
        }
        if (Long.valueOf(2).equals(result)) {
            return Result.fail("不能重复下单");
        }
        if (!Long.valueOf(0).equals(result)) {
            return Result.fail("系统繁忙，请稍后重试");
        }

        Long orderId = redisIdWorker.nextId("voucher_order");
        VoucherOrderMessage message = new VoucherOrderMessage(
                orderId,
                userId,
                voucherId,
                orderId,
                LocalDateTime.now()
        );

        try {
            rocketMQTemplate.syncSend(voucherProperties.getOrderTopic(), message, 3000);
        } catch (Exception e) {
            rollbackRedisSeckill(voucherId, userId, orderId, orderId, "VoucherService");
            return Result.fail("系统繁忙，请稍后重试");
        }

        return Result.ok(orderId);
    }

    public void handleVoucherOrder(VoucherOrderMessage message) {
        validateMessage(message);
        RLock lock = redissonClient.getLock(
                RedisConstants.LOCK_VOUCHER_ORDER_KEY + message.getUserId() + ":" + message.getVoucherId()
        );
        boolean locked = false;
        try {
            locked = lock.tryLock(1, 10, TimeUnit.SECONDS);
            if (!locked) {
                throw new VoucherOrderLockBusyException("Could not acquire voucher order lock");
            }

            Boolean created = transactionTemplate.execute(status -> createVoucherOrder(message));
            if (Boolean.FALSE.equals(created)) {
                rollbackRedisSeckill(
                        message.getVoucherId(),
                        message.getUserId(),
                        message.getOrderId(),
                        message.getTraceId(),
                        "VoucherOrderConsumer"
                );
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while acquiring voucher order lock", e);
        } catch (VoucherOrderLockBusyException e) {
            throw e;
        } catch (RuntimeException e) {
            rollbackRedisSeckill(
                    message.getVoucherId(),
                    message.getUserId(),
                    message.getOrderId(),
                    message.getTraceId(),
                    "VoucherOrderConsumer"
            );
            throw e;
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private Boolean createVoucherOrder(VoucherOrderMessage message) {
        if (voucherRepository.existsOrder(message.getUserId(), message.getVoucherId())) {
            return true;
        }

        int updated = voucherRepository.deductStock(message.getVoucherId());
        if (updated == 0) {
            return false;
        }

        Integer afterQty = voucherRepository.queryStock(message.getVoucherId());
        Integer beforeQty = afterQty == null ? null : afterQty + 1;
        voucherRepository.insertVoucherOrder(message.getOrderId(), message.getUserId(), message.getVoucherId());
        voucherRepository.insertVoucherOrderRouter(
                redisIdWorker.nextId("voucher_order_router"),
                message.getOrderId(),
                message.getUserId(),
                message.getVoucherId()
        );
        voucherRepository.insertReconcileLog(
                redisIdWorker.nextId("voucher_reconcile_log"),
                message.getOrderId(),
                message.getUserId(),
                message.getVoucherId(),
                message.getOrderId().toString(),
                beforeQty,
                -1,
                afterQty,
                message.getTraceId()
        );
        return true;
    }

    private Result<Void> validateVoucher(VoucherDTO dto) {
        if (dto.getActualValue() < dto.getPayValue()) {
            return Result.fail("抵扣金额不能小于支付金额");
        }
        if (shopRepository.findById(dto.getShopId()) == null) {
            return Result.fail("商户不存在");
        }
        return Result.ok(null);
    }

    private Result<Void> validateSeckillRequest(Long voucherId) {
        if (voucherId == null || voucherId <= 0) {
            return Result.fail("优惠券ID不合法");
        }

        Voucher voucher = voucherRepository.findVoucherById(voucherId);
        if (voucher == null || voucher.getType() != VOUCHER_TYPE_SECKILL) {
            return Result.fail("优惠券不存在");
        }
        if (voucher.getStatus() != VOUCHER_STATUS_ON_SHELF) {
            return Result.fail("优惠券不可用");
        }

        SeckillVoucher seckillVoucher = voucherRepository.findSeckillVoucherByVoucherId(voucherId);
        if (seckillVoucher == null) {
            return Result.fail("优惠券不存在");
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(seckillVoucher.getBeginTime())) {
            return Result.fail("秒杀尚未开始");
        }
        if (now.isAfter(seckillVoucher.getEndTime())) {
            return Result.fail("秒杀已经结束");
        }
        return Result.ok(null);
    }

    private void rollbackRedisSeckill(Long voucherId, Long userId, Long orderId, Long traceId, String source) {
        try {
            Long result = stringRedisTemplate.execute(
                    ROLLBACK_SCRIPT,
                    List.of(RedisConstants.SECKILL_STOCK_KEY + voucherId, RedisConstants.SECKILL_ORDER_KEY + voucherId),
                    userId.toString()
            );
            if (!Long.valueOf(0).equals(result)) {
                writeRollbackFailureLog(voucherId, userId, orderId, traceId, "Redis回滚返回异常", result, source);
            }
        } catch (Exception e) {
            writeRollbackFailureLog(voucherId, userId, orderId, traceId, e.getMessage(), null, source);
        }
    }

    private void writeRollbackFailureLog(Long voucherId,
                                         Long userId,
                                         Long orderId,
                                         Long traceId,
                                         String detail,
                                         Long resultCode,
                                         String source) {
        try {
            voucherRepository.insertRollbackFailureLog(
                    redisIdWorker.nextId("rollback_failure_log"),
                    voucherId,
                    userId,
                    orderId,
                    traceId,
                    detail,
                    resultCode == null ? null : resultCode.intValue(),
                    source
            );
        } catch (Exception ignored) {
            // 避免补偿日志写入失败影响主异常传播。
        }
    }

    private void validateMessage(VoucherOrderMessage message) {
        if (message == null
                || message.getOrderId() == null
                || message.getUserId() == null
                || message.getVoucherId() == null) {
            throw new IllegalArgumentException("Invalid voucher order message");
        }
    }

    private static class VoucherOrderLockBusyException extends RuntimeException {
        VoucherOrderLockBusyException(String message) {
            super(message);
        }
    }
}
