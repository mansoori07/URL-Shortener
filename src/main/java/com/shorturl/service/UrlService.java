package com.shorturl.service;

import com.shorturl.dto.CreateUrlRequest;
import com.shorturl.dto.CreateUrlResponse;
import com.shorturl.entity.IdempotencyRequest;
import com.shorturl.entity.IdempotencyStatus;
import com.shorturl.entity.Url;
import com.shorturl.exception.UrlNotFoundException;
import com.shorturl.repository.UrlRepository;
import com.shorturl.util.Base62Encoder;
import com.shorturl.util.RandomShortCodeGenerator;
import com.shorturl.util.SnowflakeIdGenerator;
import lombok.AllArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@AllArgsConstructor
public class UrlService {

    private final UrlRepository urlRepository;
    private final UrlCacheService urlCacheService;
    private final RandomShortCodeGenerator shortCodeGenerator;
    private final SnowflakeIdGenerator idGenerator;
    private final IdempotencyService idempotencyService;

    public String getOriginalUrl(String shortCode){

        String cachedUrl = urlCacheService.get(shortCode);
        if(cachedUrl != null){
            return cachedUrl;
        }

        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() ->
                        new UrlNotFoundException(
                                "URL not found for short code: " + shortCode
                        )
                );

        if(!url.isActive()){
            throw new UrlNotFoundException("URL is no longer active");
        }

        if(url.getExpiresAt() != null && url.getExpiresAt().isBefore(LocalDateTime.now())){
            throw new UrlNotFoundException("URL has expired");
        }

        urlCacheService.put(url.getShortCode(), url.getOriginalUrl());

        return url.getOriginalUrl();

    }

    public CreateUrlResponse createShortUrl(CreateUrlRequest request, String idempotencyKey) {

        Optional<IdempotencyRequest> existing = idempotencyService.findExisting(idempotencyKey);

        if(existing.isPresent()){
            IdempotencyRequest record = existing.get();
            idempotencyService.validRequest(record, request.getOriginalUrl());

            if(record.getStatus() == IdempotencyStatus.COMPLETED){
                return new CreateUrlResponse(record.getShortCode(), "http://localhost:8080/" + record.getShortCode());
            }
        }

        IdempotencyRequest idempotencyRequest = idempotencyService.createProcessingRecord(idempotencyKey, request.getOriginalUrl());

        /*
         * Important:
         * Another instance may have won the race.
         */

        if(idempotencyRequest.getIdempotencyKey().equals(idempotencyKey)){
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
                    // Possible short-code collision.
                    // Generate a new short code and retry.
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

}
