package com.roucoux.cairn.infrastructure.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

final class PasswordGuessThrottle {

    static final int FREE_ATTEMPTS = 5;

    static final Duration MAX_DELAY = Duration.ofMinutes(15);

    static final int MAX_TRACKED_ADDRESSES = 10_000;

    private static final int MAX_EXPONENT = 20;

    private record Attempts(int count, Instant blockedUntil) {}

    private final Clock clock;
    private final int maxTracked;
    private final Map<String, Attempts> byAddress = new LinkedHashMap<>();

    PasswordGuessThrottle(Clock clock) {
        this(clock, MAX_TRACKED_ADDRESSES);
    }

    PasswordGuessThrottle(Clock clock, int maxTracked) {
        this.clock = clock;
        this.maxTracked = maxTracked;
    }

    synchronized Duration admit(String address) {
        Instant now = clock.instant();
        Attempts previous = byAddress.remove(address);
        if (previous != null && isForgotten(previous, now)) {
            previous = null;
        }
        if (previous != null) {
            Duration remaining = Duration.between(now, previous.blockedUntil());
            if (!remaining.isNegative() && !remaining.isZero()) {
                byAddress.put(address, previous);
                return remaining;
            }
        } else if (byAddress.size() >= maxTracked) {
            makeRoom(now);
        }
        int count = previous == null ? 1 : previous.count() + 1;
        byAddress.put(address, new Attempts(count, blockedUntil(count, now)));
        return Duration.ZERO;
    }

    synchronized void recordSuccess(String address) {
        byAddress.remove(address);
    }

    synchronized int trackedAddresses() {
        return byAddress.size();
    }

    private static Instant blockedUntil(int count, Instant now) {
        if (count < FREE_ATTEMPTS) {
            return now;
        }
        long seconds = 1L << Math.min(count - FREE_ATTEMPTS, MAX_EXPONENT);
        return now.plusSeconds(Math.min(seconds, MAX_DELAY.toSeconds()));
    }

    private static boolean isForgotten(Attempts attempts, Instant now) {
        return !attempts.blockedUntil().plus(MAX_DELAY).isAfter(now);
    }

    private void makeRoom(Instant now) {
        byAddress.values().removeIf(attempts -> isForgotten(attempts, now));
        Iterator<String> oldest = byAddress.keySet().iterator();
        while (byAddress.size() >= maxTracked && oldest.hasNext()) {
            oldest.next();
            oldest.remove();
        }
    }
}
