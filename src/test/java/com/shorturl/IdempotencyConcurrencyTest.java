package com.shorturl;

import com.shorturl.dto.CreateUrlRequest;
import com.shorturl.dto.CreateUrlResponse;
import com.shorturl.repository.UrlRepository;
import com.shorturl.service.UrlCreationTransactionService;
import com.shorturl.service.UrlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Testcontainers
public class IdempotencyConcurrencyTest {

    static{
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
    }

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:14")
            .withDatabaseName("url_shortener")
            .withUsername("postgres")
            .withPassword("postgres");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry){
        System.out.println("Configuring Database : " + postgres.getJdbcUrl());
        System.out.println("Configuring Database : " + postgres.getPassword());
        System.out.println("Configuring Database : " + postgres.getUsername());
        System.out.println("JVM TimeZone = " + System.getProperty("user.timezone"));
        System.out.println("Default TZ = " + java.util.TimeZone.getDefault().getID());
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("sprinFg.datasource.password", postgres::getPassword);
    }

    @Autowired
    private UrlCreationTransactionService urlService;

    @Autowired
    private UrlRepository urlRepository;

    @Test
    void concurrentRequestsShouldBeIdempotentKeyCreateOnlyOneUrl() throws InterruptedException, ExecutionException {
        // Implement your test logic here to simulate concurrent requests
        // and verify that the service behaves idempotently.

        int numberOfRequest = 10;

        ExecutorService executorService = Executors.newFixedThreadPool(numberOfRequest);

        String idempotencyKey = "concurrent-test-key-123";

        CreateUrlRequest request = new CreateUrlRequest("https://example.com");

        List<Callable<CreateUrlResponse>> tasks = new ArrayList<>();

        for (int i = 0; i < numberOfRequest; i++) {
            tasks.add(() -> urlService.createShortUrl(request, idempotencyKey));
        }

        List<Future<CreateUrlResponse>> futures = executorService.invokeAll(tasks);
        executorService.shutdown();

        Set<String> shortUrls = new HashSet<>();
        for(Future<CreateUrlResponse> future : futures) {
            CreateUrlResponse response = future.get();
            System.out.println("Short URL: " + response.getShortUrl());
            shortUrls.add(response.getShortUrl());
        }

        assertEquals(1, shortUrls.size());
        assertEquals(1, urlRepository.count());



    }
}
