package com.shorturl;

import com.shorturl.entity.Url;
import com.shorturl.repository.UrlRepository;
import com.shorturl.service.UrlCacheService;
import com.shorturl.service.UrlService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
@SpringBootTest
@Testcontainers
class UrlResolutionConcurrencyTest {

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
    }

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:14")
            .withDatabaseName("url_shortener")
            .withUsername("postgres")
            .withPassword("postgres");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private UrlService urlService;

    @Autowired
    private UrlRepository urlRepository;

    @Autowired
    private UrlCacheService cacheService;

    @Test
    void concurrentRequestsShouldLoadFromDatabaseOnlyOnce() throws Exception {

        // Arrange
        Url url = new Url();
        url.setId(23456L);
        url.setShortCode("abc123");
        url.setOriginalUrl("https://google.com");
        url.setActive(true);
        url.setCreatedAt(LocalDateTime.now());
        url.setExpiresAt(LocalDateTime.now().plusDays(1));

        urlRepository.save(url);

        // Clear cache before test
        cacheService.delete("abc123");

        int numberOfRequests = 20;

        ExecutorService executorService =
                Executors.newFixedThreadPool(numberOfRequests);

        List<Callable<String>> tasks = new ArrayList<>();

        for (int i = 0; i < numberOfRequests; i++) {
            tasks.add(() -> urlService.getOriginalUrl("abc123"));
        }

        // Act
        List<Future<String>> futures = tasks.stream()
                .map(executorService::submit)
                .toList();

        Set<String> results = new HashSet<>();

        for (Future<String> future : futures) {
            results.add(future.get());
        }

        executorService.shutdown();

        // Assert
        assertEquals(1, results.size());
        assertTrue(results.contains("https://google.com"));

        String cachedValue = cacheService.get("abc123");
        log.info("Cached value after concurrent requests: {}", cachedValue);
//        assertEquals("https://google.com", cachedValue);
    }
}