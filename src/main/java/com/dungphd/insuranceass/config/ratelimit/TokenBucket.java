package com.dungphd.insuranceass.config.ratelimit;

/**
 * High-performance, thread-safe Token Bucket algorithm implementation.
 */
public class TokenBucket {

    private final long capacity;
    private final double refillRatePerSecond;
    private double availableTokens;
    private long lastRefillTimestampNanos;
    private long lastAccessTimestampMillis;

    public TokenBucket(long capacity, double refillRatePerSecond) {
        this.capacity = capacity;
        this.refillRatePerSecond = refillRatePerSecond;
        this.availableTokens = capacity;
        this.lastRefillTimestampNanos = System.nanoTime();
        this.lastAccessTimestampMillis = System.currentTimeMillis();
    }

    public synchronized boolean tryConsume(long tokens) {
        refill();
        lastAccessTimestampMillis = System.currentTimeMillis();
        if (availableTokens >= tokens) {
            availableTokens -= tokens;
            return true;
        }
        return false;
    }

    public synchronized long getAvailableTokens() {
        refill();
        return (long) Math.floor(availableTokens);
    }

    public synchronized long getCapacity() {
        return capacity;
    }

    public synchronized long getSecondsUntilNextRefill() {
        if (availableTokens >= 1.0) {
            return 0;
        }
        double needed = 1.0 - availableTokens;
        long seconds = (long) Math.ceil(needed / refillRatePerSecond);
        return Math.max(1, seconds);
    }

    public synchronized long getLastAccessTimestampMillis() {
        return lastAccessTimestampMillis;
    }

    private void refill() {
        long nowNanos = System.nanoTime();
        long elapsedNanos = nowNanos - lastRefillTimestampNanos;
        if (elapsedNanos > 0) {
            double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
            double refillTokens = elapsedSeconds * refillRatePerSecond;
            availableTokens = Math.min(capacity, availableTokens + refillTokens);
            lastRefillTimestampNanos = nowNanos;
        }
    }
}
