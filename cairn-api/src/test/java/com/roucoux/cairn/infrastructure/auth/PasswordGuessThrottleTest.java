package com.roucoux.cairn.infrastructure.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PasswordGuessThrottleTest {

    private static final String ALEX = "203.0.113.7";
    private static final String OTHER = "198.51.100.9";

    private final MutableClock clock = new MutableClock();
    private final PasswordGuessThrottle throttle = new PasswordGuessThrottle(clock);

    @Test
    void admitsTheFirstFiveAttempts() {
        for (int i = 0; i < PasswordGuessThrottle.FREE_ATTEMPTS; i++) {
            assertThat(throttle.admit(ALEX)).isZero();
        }
    }

    @Test
    void refusesForOneSecondRightAfterTheFifthAttempt() {
        attempts(ALEX, 5);

        assertThat(throttle.admit(ALEX)).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void doublesTheDelayOnEachFurtherAdmittedAttempt() {
        attempts(ALEX, 5);
        clock.advance(Duration.ofSeconds(1));
        assertThat(throttle.admit(ALEX)).isZero();
        assertThat(throttle.admit(ALEX)).isEqualTo(Duration.ofSeconds(2));

        clock.advance(Duration.ofSeconds(2));
        assertThat(throttle.admit(ALEX)).isZero();
        assertThat(throttle.admit(ALEX)).isEqualTo(Duration.ofSeconds(4));
    }

    @Test
    void neverRefusesForMoreThanFifteenMinutes() {
        Duration longest = Duration.ZERO;
        for (int i = 0; i < 60; i++) {
            Duration delay = throttle.admit(ALEX);
            longest = longest.compareTo(delay) >= 0 ? longest : delay;
            clock.advance(delay);
        }

        assertThat(longest).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void startsAgainFromOneOnceTheLastBlockLapsedMoreThanFifteenMinutesAgo() {
        attempts(ALEX, 6);
        clock.advance(Duration.ofMinutes(16));

        attempts(ALEX, PasswordGuessThrottle.FREE_ATTEMPTS - 1);

        assertThat(throttle.admit(ALEX)).isZero();
        assertThat(throttle.admit(ALEX)).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void keepsEscalatingWhenTheLastBlockLapsedLessThanFifteenMinutesAgo() {
        attempts(ALEX, 6);
        clock.advance(Duration.ofMinutes(10));

        assertThat(throttle.admit(ALEX)).isZero();
        assertThat(throttle.admit(ALEX)).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void neverBuildsABlockFromTyposSpreadOverTime() {
        for (int i = 0; i < 50; i++) {
            clock.advance(Duration.ofMinutes(16));
            assertThat(throttle.admit(ALEX)).isZero();
        }
        assertThat(throttle.admit(ALEX)).isZero();
    }

    @Test
    void keepsAddressesIndependent() {
        attempts(ALEX, 6);

        assertThat(throttle.admit(OTHER)).isZero();
        assertThat(throttle.admit(ALEX)).isPositive();
    }

    @Test
    void aSuccessResetsOnlyItsOwnAddress() {
        attempts(ALEX, 6);
        attempts(OTHER, 6);

        throttle.recordSuccess(ALEX);

        assertThat(throttle.admit(ALEX)).isZero();
        assertThat(throttle.admit(OTHER)).isPositive();
    }

    @Test
    void admitsAtMostOneOfManyConcurrentAttemptsWhenFourAreAlreadyCounted() throws Exception {
        attempts(ALEX, 4);
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger admitted = new AtomicInteger();
        try {
            List<Future<?>> done = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                done.add(pool.submit(() -> {
                    start.await();
                    if (throttle.admit(ALEX).isZero()) {
                        admitted.incrementAndGet();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : done) {
                future.get();
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(admitted.get()).isEqualTo(1);
    }

    @Test
    void keepsTheNumberOfTrackedAddressesBounded() {
        PasswordGuessThrottle small = new PasswordGuessThrottle(clock, 3);

        for (int i = 0; i < 10; i++) {
            small.admit("10.0.0." + i);
        }

        assertThat(small.trackedAddresses()).isEqualTo(3);
    }

    @Test
    void evictsTheOldestAddressWhenFullAndNothingHasExpired() {
        PasswordGuessThrottle small = new PasswordGuessThrottle(clock, 2);
        attempts(small, "first", 6);
        attempts(small, "second", 6);

        small.admit("third");

        assertThat(small.admit("second")).isPositive();
        assertThat(small.admit("first")).isZero();
    }

    @Test
    void dropsExpiredBlocksBeforeEvictingALiveOne() {
        PasswordGuessThrottle small = new PasswordGuessThrottle(clock, 2);
        attempts(small, "stale", 6);
        attempts(small, "live", 6);
        clock.advance(Duration.ofMinutes(16));
        attempts(small, "live", 6);

        small.admit("new");

        assertThat(small.trackedAddresses()).isEqualTo(2);
        assertThat(small.admit("live")).isPositive();
    }

    @Test
    void keepsABlockThatLapsedLessThanFifteenMinutesAgoWhenMakingRoom() {
        PasswordGuessThrottle small = new PasswordGuessThrottle(clock, 3);
        attempts(small, "stale", 6);
        clock.advance(Duration.ofMinutes(16));
        attempts(small, "lapsed", 6);
        clock.advance(Duration.ofMinutes(10));
        small.admit("third");

        small.admit("new");

        assertThat(small.trackedAddresses()).isEqualTo(3);
        assertThat(small.admit("lapsed")).isZero();
        assertThat(small.admit("lapsed")).isEqualTo(Duration.ofSeconds(2));
    }

    private void attempts(String address, int times) {
        attempts(throttle, address, times);
    }

    private static void attempts(PasswordGuessThrottle target, String address, int times) {
        for (int i = 0; i < times; i++) {
            target.admit(address);
        }
    }
}
