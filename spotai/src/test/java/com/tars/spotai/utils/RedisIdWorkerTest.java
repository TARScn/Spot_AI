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

@SpringBootTest
class RedisIdWorkerTest {
    private static final int THREAD_COUNT = 300;
    private static final int IDS_PER_THREAD = 100;
    private static final int TOTAL_IDS = THREAD_COUNT * IDS_PER_THREAD;

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Test
    void generatesUniqueOrderIdsUnderConcurrentLoad() throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        Set<Long> ids = java.util.concurrent.ConcurrentHashMap.newKeySet(TOTAL_IDS);

        Runnable task = () -> {
            try {
                for (int i = 0; i < IDS_PER_THREAD; i++) {
                    ids.add(redisIdWorker.nextId("voucher_order"));
                }
            } finally {
                latch.countDown();
            }
        };

        long begin = System.currentTimeMillis();
        for (int i = 0; i < THREAD_COUNT; i++) {
            executorService.submit(task);
        }
        boolean finished = latch.await(30, TimeUnit.SECONDS);
        long end = System.currentTimeMillis();

        executorService.shutdown();
        if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
            executorService.shutdownNow();
        }

        if (!finished) {
            fail("Redis ID worker pressure test did not finish within 30 seconds");
        }
        System.out.println("Generated " + TOTAL_IDS + " IDs in " + (end - begin) + " ms");
        assertThat(ids).hasSize(TOTAL_IDS);
    }
}