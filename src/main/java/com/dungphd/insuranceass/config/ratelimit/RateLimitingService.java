package com.dungphd.insuranceass.config.ratelimit;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@Service
public class RateLimitingService {

    @Getter
    @Value("${app.security.rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${app.security.rate-limit.auth.capacity:10}")
    private long authCapacity;

    @Value("${app.security.rate-limit.auth.duration-seconds:60}")
    private long authDurationSeconds;

    @Value("${app.security.rate-limit.docs.capacity:15}")
    private long docsCapacity;

    @Value("${app.security.rate-limit.docs.duration-seconds:60}")
    private long docsDurationSeconds;

    @Value("${app.security.rate-limit.general.capacity:100}")
    private long generalCapacity;

    @Value("${app.security.rate-limit.general.duration-seconds:60}")
    private long generalDurationSeconds;

    private final Map<String, TokenBucket> bucketCache = new ConcurrentHashMap<>();
    private ScheduledExecutorService cleanupExecutor;

    public enum RequestTier {
        AUTH,
        DOCUMENTS,
        GENERAL
    }

    @Data
    @Builder
    public static class RateLimitResult {
        private boolean allowed;
        private long remainingTokens;
        private long limit;
        private long retryAfterSeconds;
    }

    @PostConstruct
    public void init() {
        cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rate-limit-cleanup");
            t.setDaemon(true);
            return t;
        });
        // Evict idle buckets older than 15 minutes every 5 minutes
        cleanupExecutor.scheduleAtFixedRate(this::evictIdleBuckets, 5, 5, TimeUnit.MINUTES);
    }

    @PreDestroy
    public void shutdown() {
        if (cleanupExecutor != null) {
            cleanupExecutor.shutdownNow();
        }
    }

    public RateLimitResult checkRateLimit(HttpServletRequest request) {
        if (!enabled) {
            return RateLimitResult.builder()
                    .allowed(true)
                    .remainingTokens(Long.MAX_VALUE)
                    .limit(Long.MAX_VALUE)
                    .retryAfterSeconds(0)
                    .build();
        }

        String clientIp = resolveClientIp(request);
        String path = request.getRequestURI();
        RequestTier tier = resolveTier(path);

        String cacheKey = clientIp + ":" + tier.name();
        TokenBucket bucket = bucketCache.computeIfAbsent(cacheKey, k -> createBucketForTier(tier));

        boolean allowed = bucket.tryConsume(1);
        long remaining = bucket.getAvailableTokens();
        long retryAfter = allowed ? 0 : bucket.getSecondsUntilNextRefill();

        if (!allowed) {
            log.warn("[RATE LIMIT EXCEEDED] IP {} hit rate limit for tier {} (path: {}). Retry after: {}s",
                    clientIp, tier.name(), path, retryAfter);
        }

        return RateLimitResult.builder()
                .allowed(allowed)
                .remainingTokens(remaining)
                .limit(bucket.getCapacity())
                .retryAfterSeconds(retryAfter)
                .build();
    }

    public RequestTier resolveTier(String path) {
        if (path == null) {
            return RequestTier.GENERAL;
        }
        String lower = path.toLowerCase();
        if (lower.startsWith("/auth") || lower.startsWith("/api/auth")) {
            return RequestTier.AUTH;
        }
        if (lower.contains("/documents")) {
            return RequestTier.DOCUMENTS;
        }
        return RequestTier.GENERAL;
    }

    public String resolveClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // First IP in comma-separated list is the client IP
            String[] parts = xForwardedFor.split(",");
            String clientIp = parts[0].trim();
            if (isValidIp(clientIp)) {
                return clientIp;
            }
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank() && isValidIp(xRealIp.trim())) {
            return xRealIp.trim();
        }

        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "127.0.0.1";
    }

    private boolean isValidIp(String ip) {
        if (ip == null || ip.isBlank() || ip.length() > 45) {
            return false;
        }
        // Basic sanity check to avoid header injection
        return ip.matches("^[a-fA-F0-9:.]+$");
    }

    private TokenBucket createBucketForTier(RequestTier tier) {
        return switch (tier) {
            case AUTH -> new TokenBucket(authCapacity, (double) authCapacity / authDurationSeconds);
            case DOCUMENTS -> new TokenBucket(docsCapacity, (double) docsCapacity / docsDurationSeconds);
            case GENERAL -> new TokenBucket(generalCapacity, (double) generalCapacity / generalDurationSeconds);
        };
    }

    private void evictIdleBuckets() {
        long threshold = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(15);
        int initialSize = bucketCache.size();
        bucketCache.entrySet().removeIf(entry -> entry.getValue().getLastAccessTimestampMillis() < threshold);
        int evicted = initialSize - bucketCache.size();
        if (evicted > 0) {
            log.debug("[RATE LIMIT CLEANUP] Evicted {} idle token buckets. Active buckets: {}", evicted, bucketCache.size());
        }
    }

    public void clearCache() {
        bucketCache.clear();
    }
}
