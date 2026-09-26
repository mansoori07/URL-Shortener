package com.shorturl.service;

import com.shorturl.dto.CreateUrlRequest;
import com.shorturl.dto.CreateUrlResponse;
import com.shorturl.entity.Url;
import com.shorturl.exception.UrlNotFoundException;
import com.shorturl.repository.UrlRepository;
import com.shorturl.util.RandomShortCodeGenerator;
import com.shorturl.util.SnowflakeIdGenerator;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@AllArgsConstructor
public class UrlService {

    private final UrlRepository urlRepository;
    private final UrlCacheService cacheService;
    private final UrlCreationTransactionService urlCreationTransactionService;
    private final RandomShortCodeGenerator shortCodeGenerator;
    private final SnowflakeIdGenerator idGenerator;
    private final IdempotencyService idempotencyService;

    public String getOriginalUrl(String shortCode) {

        String localValue = cacheService.getFromLocalCache(shortCode);

        if(localValue != null){
            return localValue;
        }

        String cachedUrl = cacheService.get(shortCode);

        if (cachedUrl != null) {
            if(cacheService.isNotFound(cachedUrl)){
                throw new UrlNotFoundException("URL not found");
            }
            cacheService.putInLocalCache(shortCode, cachedUrl);
            return cachedUrl;
        }

        String lockToken = cacheService.acquireLock(shortCode);

        if (lockToken == null) {
            log.info("Lock Token not acquired for shortCode as it is being locked by another resource");
            for (int i = 0; i < 10; i++) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Thread interrupted while waiting for lock to be released", e);
                    throw new RuntimeException(e);
                }

                localValue = cacheService.getFromLocalCache(shortCode);

                if(localValue != null){
                    return localValue;
                }

                cachedUrl = cacheService.get(shortCode);

                if (cachedUrl != null) {
                    if(cacheService.isNotFound(cachedUrl)){
                        throw new UrlNotFoundException("URL not found");
                    }
                    cacheService.putInLocalCache(shortCode, cachedUrl);
                    log.info("Cache found for shortCode after waiting for lock to be released: {}", cachedUrl);
                    return cachedUrl;
                }
            }
            throw new RuntimeException("Could not acquire cache rebuild lock.");
        }

        try {
            localValue = cacheService.getFromLocalCache(shortCode);
            if(localValue != null){
                return localValue;
            }

            log.info("Lock Token acquired for shortCode, rebuilding cache for shortCode: {}", shortCode);
            cachedUrl = cacheService.get(shortCode);
            if (cachedUrl != null) {
                if(cacheService.isNotFound(cachedUrl)){
                    throw new UrlNotFoundException("URL not found");
                }
                cacheService.putInLocalCache(shortCode, cachedUrl);
                log.info("Cache found for shortCode after acquiring lock: {}", cachedUrl);
                return cachedUrl;
            }

            log.info("Searching URL in DB for shortCode: {}", shortCode);

            Optional<Url> urlOptional = urlRepository.findByShortCode(shortCode);
            if(urlOptional.isEmpty()){
                cacheService.putNotFound(shortCode);
                throw new UrlNotFoundException("URL not found");
            }

            Url url = urlOptional.get();
            log.info("URL found in DB for shortCode: {}, originalUrl: {}", shortCode, url.getOriginalUrl());

            if (!url.isActive()) {
                throw new UrlNotFoundException("URL is inactive");
            }

            if (url.getExpiresAt() != null && url.getExpiresAt().isBefore(LocalDateTime.now())) {
                throw new UrlNotFoundException("URL has expired");
            }

            cacheService.putInLocalCache(shortCode, url.getOriginalUrl());
            cacheService.put(shortCode, url.getOriginalUrl());

            log.info("Cache updated for shortCode: {}", shortCode);

            return url.getOriginalUrl();
        } finally {
            log.info("Cache released for shortCode: {}", shortCode);
            cacheService.releaseLock(shortCode, lockToken);
        }
    }

    public CreateUrlResponse createShortUrl(CreateUrlRequest request, String idempotencyKey) {
        return urlCreationTransactionService.createShortUrl(request, idempotencyKey);
    }

/*    public CreateUrlResponse createShortUrl(CreateUrlRequest request, String idempotencyKey) {

        Optional<IdempotencyRequest> existing = idempotencyService.findExisting(idempotencyKey);

        if(existing.isPresent()){
            IdempotencyRequest record = existing.get();
            idempotencyService.validRequest(record, request.getOriginalUrl());

            if(record.getStatus() == IdempotencyStatus.COMPLETED){
                return new CreateUrlResponse(record.getShortCode(), "http://localhost:8080/" + record.getShortCode());
            }
        }

        IdempotencyRequest idempotencyRequest = idempotencyService.createProcessingRecord(idempotencyKey, request.getOriginalUrl());

        if(!idempotencyRequest.getIdempotencyKey().equals(idempotencyKey)){
            throw new IllegalArgumentException("Unexpected Idempotency key");
        }

        if(idempotencyRequest.getStatus() == IdempotencyStatus.COMPLETED){
            return new CreateUrlResponse(idempotencyRequest.getShortCode(), "http://localhost:8080/" + idempotencyRequest.getShortCode());
        }


        try {
            final int maxAttempts = 5;
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                long id = idGenerator.generateId();

                String shortCode = new Base62Encoder().encode(id);
                Url url = new Url();
                url.setId(id);
                url.setShortCode(shortCode);
                url.setOriginalUrl(request.getOriginalUrl());
                url.setCreatedAt(LocalDateTime.now());
                url.setActive(true);

                try {
                    urlRepository.save(url);
                    idempotencyService.markCompleted(idempotencyRequest, url.getShortCode());

                    String shortUrl = "http://localhost:8080/" + shortCode;
                    return new CreateUrlResponse(shortCode, shortUrl);
                } catch (DataIntegrityViolationException e) {
                    if (attempt == maxAttempts) {
                        throw new RuntimeException("Unable to generate a unique short code");
                    }
                }
            }
        } catch (Exception e) {
            idempotencyService.markFailed(idempotencyRequest);
            throw new RuntimeException("Unable to create short URL", e);
        }
        throw new RuntimeException("Unable to create short URL");
    }
*/

}
