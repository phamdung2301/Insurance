package com.dungphd.insuranceass.config.ratelimit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenBucketTest {

    @Test
    void tryConsume_WithinCapacity_ReturnsTrue() {
        TokenBucket bucket = new TokenBucket(5, 1.0); // 5 tokens capacity, 1 token/sec refill

        assertTrue(bucket.tryConsume(1));
        assertTrue(bucket.tryConsume(2));
        assertTrue(bucket.tryConsume(2));
        // Capacity now exhausted
        assertFalse(bucket.tryConsume(1));
    }

    @Test
    void getSecondsUntilNextRefill_WhenEmpty_ReturnsPositive() {
        TokenBucket bucket = new TokenBucket(1, 0.5); // Refills 0.5 token/sec => 2 seconds for 1 token

        assertTrue(bucket.tryConsume(1));
        assertFalse(bucket.tryConsume(1));
        long waitSeconds = bucket.getSecondsUntilNextRefill();
        assertTrue(waitSeconds >= 1);
    }

    @Test
    void getAvailableTokens_InitiallyEqualsCapacity() {
        TokenBucket bucket = new TokenBucket(10, 2.0);
        assertEquals(10, bucket.getAvailableTokens());
    }

    @Test
    void tryConsume_TokensExceedingAvailable_ReturnsFalse() {
        TokenBucket bucket = new TokenBucket(3, 1.0);
        assertFalse(bucket.tryConsume(5));
        assertEquals(3, bucket.getAvailableTokens());
    }
}
