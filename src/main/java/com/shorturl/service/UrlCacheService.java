package com.shorturl.service;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.AllArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

@Service
@AllArgsConstructor
public class UrlCacheService {

    private static final String NOT_FOUND = "__NOT_FOUND__";

    private static final String CACHE_PREFIX = "url:";
    private static final String LOCK_PREFIX = "lock:url:";

    private static final Duration CACHE_TTL = Duration.ofHours(1);
    private static final Duration LOCK_TTL = Duration.ofMillis(100);
    private static final Duration NEGATIVE_CACHE_TTL = Duration.ofSeconds(30);

    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
            """
                    if redis.call('get', KEYS[1]) == ARGV[1] then
                        return redis.call('del', KEYS[1])
                    else
                        return 0
                    end
            """, Long.class
    );

    private final RedisTemplate<String, String> redisTemplate;
    private final Cache<String, String> localCache;

    public void put(String shortCode, String originalUrl) {
        redisTemplate.opsForValue().set(buildCachedKey(shortCode), originalUrl, CACHE_TTL);
    }

    public String get(String shortCode) {
        return redisTemplate.opsForValue().get(buildCachedKey(shortCode));
    }

    public void delete(String shortCode) {
        redisTemplate.delete(buildCachedKey(shortCode));
    }

    private String buildCachedKey(String shortCode) {
        return CACHE_PREFIX + shortCode;
    }

    public String acquireLock(String shortCode) {
        String token = UUID.randomUUID().toString();

        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(buildLockKey(shortCode), token, LOCK_TTL);
        if (Boolean.TRUE.equals(acquired)) {
            return token;
        }
        return null;
    }

    public void releaseLock(String shortCode, String token) {
        String lockKey = buildLockKey(shortCode);
        redisTemplate.execute(
                RELEASE_LOCK_SCRIPT,
                Collections.singletonList(lockKey),
                token
        );
//        String currentToken = redisTemplate.opsForValue().get(lockKey);
//        if (token.equals(currentToken)) {
//            redisTemplate.delete(lockKey);
//        }
    }

    private String buildLockKey(String shortCode) {
        return LOCK_PREFIX + shortCode;
    }

    public void putNotFound(String shortCode){
        String key = buildCachedKey(shortCode);
        redisTemplate.opsForValue().set(key, NOT_FOUND, NEGATIVE_CACHE_TTL);
    }

    public boolean isNotFound(String value) {
        return NOT_FOUND.equals(value);
    }

    public String getFromLocalCache(String shortCode){
        return localCache.getIfPresent(shortCode);
    }

    public void putInLocalCache(String shortCode, String originalUrl){
        localCache.put(shortCode, originalUrl);
    }

    public void invalidate(String shortCode){
        localCache.invalidate(shortCode);
        redisTemplate.delete(shortCode);
    }
}
