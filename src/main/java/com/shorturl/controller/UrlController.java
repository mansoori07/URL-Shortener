package com.shorturl.controller;

import com.shorturl.dto.CreateUrlRequest;
import com.shorturl.dto.CreateUrlResponse;
import com.shorturl.service.UrlCreationTransactionService;
import com.shorturl.service.UrlService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Slf4j
@RestController
@RequestMapping("/api/v1/urls")
@RequiredArgsConstructor
public class UrlController {
    private final UrlService urlService;
    private final UrlCreationTransactionService urlCreationTransactionService;

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode) {
        String originalUrl = urlService.getOriginalUrl(shortCode);
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(originalUrl));

        return ResponseEntity
                .status(HttpStatus.FOUND)
                .headers(headers)
                .build();

    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateUrlResponse createUrl(@Valid @RequestBody CreateUrlRequest request,
                                       @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey){
        return urlCreationTransactionService.create(request, idempotencyKey);
    }

    @PostMapping("/list")
    @ResponseStatus(HttpStatus.CREATED)
    public List<CreateUrlResponse> createUrl(@Valid @RequestBody List<CreateUrlRequest> requests,
                                             @RequestHeader("Idempotency-Key") String idempotencyKey){
//        return urlService.createShortUrl(requests);
        LocalDateTime now = LocalDateTime.now();
        List<CreateUrlResponse> responses = new ArrayList<>();
        log.info("Received {} requests to create short URLs", requests.size());
        log.info("CPU PROCESSOR: {}", Runtime.getRuntime().availableProcessors());
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        List<Future<CreateUrlResponse>> futures = new ArrayList<>();

        for(CreateUrlRequest request : requests){
            futures.add(executor.submit(() -> urlCreationTransactionService.create(request, idempotencyKey)));
        }

        for(Future<CreateUrlResponse> future : futures){
            try {
                CreateUrlResponse response = future.get();
                responses.add(response);
                log.info("Created short URL: {}", response.getShortUrl());
            } catch (Exception e) {
                log.error("Error creating short URL", e);
            }
        }
        log.info("Responses created: {}", responses.size());
        executor.shutdown();
        log.info("Total Time taken to complete this request in ms: {}", Duration.between(now, LocalDateTime.now()).toMillis());
        return responses;
    }

}
