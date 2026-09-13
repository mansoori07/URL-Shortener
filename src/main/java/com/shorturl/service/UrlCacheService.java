package com.shorturl.service;

import lombok.AllArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@AllArgsConstructor
public class UrlCacheService {

    private static final String KEY_PREFIX = "url:";

    private final RedisTemplate<String, String> redisTemplate;

    public void put(String shortCode, String originalUrl) {
        redisTemplate.opsForValue().set(buildKey(shortCode), originalUrl, Duration.ofHours(1));
    }

    public String get(String shortCode) {
        return redisTemplate.opsForValue().get(buildKey(shortCode));
    }

    public void delete(String shortCode) {
        redisTemplate.delete(buildKey(shortCode));
    }

    private String buildKey(String shortCode) {
        return KEY_PREFIX + shortCode;
    }

}
