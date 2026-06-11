package com.tars.spotai.utils;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * RedisIdWorker 并发压力测试
 *
 * 1. 验证 RedisIdWorker 在高并发下生成的 ID 全局唯一
 * 2. 验证 RedisIdWorker 在限时内能完成预期数量的 ID 生成
 * 3. 整体耗时作为性能参考输出到控制台
 */
@SpringBootTest
class RedisIdWorkerTest {
    /* ---- 并发参数 ---- */
    private static final int THREAD_COUNT = 300;    // 并发线程数
    private static final int IDS_PER_THREAD = 100;  // 每个线程生成的 ID 数量
    private static final int TOTAL_IDS = THREAD_COUNT * IDS_PER_THREAD;  // 预期总 ID 数

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Test
    void generatesUniqueOrderIdsUnderConcurrentLoad() throws InterruptedException {
        /* 1. 准备线程池和同步工具 */
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        Set<Long> ids = java.util.concurrent.ConcurrentHashMap.newKeySet(TOTAL_IDS);

        /* 2. 定义任务：每线程循环生成 ID，存入线程安全 Set */
        Runnable task = () -> {
            try {
                for (int i = 0; i < IDS_PER_THREAD; i++) {
                    ids.add(redisIdWorker.nextId("voucher_order"));
                }
            } finally {
                latch.countDown();
            }
        };

        /* 3. 提交全部任务，开始计时 */
        long begin = System.currentTimeMillis();
        for (int i = 0; i < THREAD_COUNT; i++) {
            executorService.submit(task);
        }
        /* 4. 等待所有任务完成（超时 30 秒） */
        boolean finished = latch.await(30, TimeUnit.SECONDS);
        long end = System.currentTimeMillis();

        /* 5. 优雅关闭线程池 */
        executorService.shutdown();
        if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
            executorService.shutdownNow();
        }

        /* 6. 校验：若超时则 fail；否则检查 ID 总数是否等于 Set 容量（即无重复） */
        if (!finished) {
            fail("Redis ID worker pressure test did not finish within 30 seconds");
        }
        System.out.println("Generated " + TOTAL_IDS + " IDs in " + (end - begin) + " ms");
        assertThat(ids).hasSize(TOTAL_IDS);
    }
}