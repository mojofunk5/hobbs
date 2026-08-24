package com.bonney.hobbs.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {

    @Mock
    AdminRepository adminRepository;

    @Test
    void generatesACodeWhenNoAdminExists() {
        when(adminRepository.isEmpty()).thenReturn(true);

        AdminBootstrap bootstrap = new AdminBootstrap(adminRepository);

        assertThat(bootstrap.getBootstrapCode(), is(notNullValue()));
    }

    @Test
    void doesNotGenerateACodeWhenAnAdminAlreadyExists() {
        when(adminRepository.isEmpty()).thenReturn(false);

        AdminBootstrap bootstrap = new AdminBootstrap(adminRepository);

        assertThat(bootstrap.getBootstrapCode(), is(nullValue()));
        assertThat(bootstrap.tryConsumeBootstrapCode("anything"), is(false));
    }

    @Test
    void consumingTheCorrectCodeSucceedsOnceAndConsumesIt() {
        when(adminRepository.isEmpty()).thenReturn(true);
        AdminBootstrap bootstrap = new AdminBootstrap(adminRepository);
        String code = bootstrap.getBootstrapCode();

        assertThat(bootstrap.tryConsumeBootstrapCode(code), is(true));
        assertThat(bootstrap.getBootstrapCode(), is(nullValue()));
        assertThat(bootstrap.tryConsumeBootstrapCode(code), is(false));
    }

    @Test
    void rejectsAnIncorrectCode() {
        when(adminRepository.isEmpty()).thenReturn(true);
        AdminBootstrap bootstrap = new AdminBootstrap(adminRepository);

        assertThat(bootstrap.tryConsumeBootstrapCode("wrong-code"), is(false));
        assertThat(bootstrap.getBootstrapCode(), is(notNullValue()));
    }

    @Test
    void onlyOneOfManyConcurrentCallersCanConsumeTheSameCode() throws InterruptedException {
        when(adminRepository.isEmpty()).thenReturn(true);
        AdminBootstrap bootstrap = new AdminBootstrap(adminRepository);
        String code = bootstrap.getBootstrapCode();

        int threadCount = 20;
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        barrier.await();
                    } catch (InterruptedException | BrokenBarrierException e) {
                        throw new RuntimeException(e);
                    }
                    if (bootstrap.tryConsumeBootstrapCode(code)) {
                        successCount.incrementAndGet();
                    }
                });
            }
            executor.shutdown();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS), is(true));
        } finally {
            executor.shutdownNow();
        }

        assertThat(successCount.get(), is(1));
    }
}
