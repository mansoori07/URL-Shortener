package com.shorturl.service;

import com.shorturl.dto.CreateUrlRequest;
import com.shorturl.dto.CreateUrlResponse;
import com.shorturl.entity.IdempotencyRequest;
import com.shorturl.entity.IdempotencyStatus;
import com.shorturl.entity.Url;
import com.shorturl.exception.IdempotencyKeyConflictException;
import com.shorturl.exception.IdempotencyRequestInProgressException;
import com.shorturl.repository.IdempotencyRequestRepository;
import com.shorturl.repository.UrlRepository;
import com.shorturl.util.HashUtil;
import com.shorturl.util.RandomShortCodeGenerator;
import com.shorturl.util.SnowflakeIdGenerator;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@AllArgsConstructor
public class UrlCreationTransactionService {

    private final IdempotencyRequestRepository idempotencyRepository;
    private final UrlRepository urlRepository;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final RandomShortCodeGenerator shortCodeGenerator;

    @Transactional
    public CreateUrlResponse create(CreateUrlRequest request, String idempotencyKey) {

        String requestHash = HashUtil.sha256(request.getOriginalUrl());
        System.out.println("requestHash: " + requestHash);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusHours(24);

        int inserted = idempotencyRepository.insertIfAbsent(
                idempotencyKey,
                requestHash,
                now,
                expiresAt
        );

        System.out.println("inserted: " + inserted);


        if(inserted == 0){

            System.out.println("Insertion is 0");
            IdempotencyRequest existing = idempotencyRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new IllegalArgumentException("Failed to retrieve idempotency record for key: " + idempotencyKey));


            if(!existing.getRequestHash().equals(requestHash)){
                throw new IdempotencyKeyConflictException("Idempotency key was already used with a different request.");
            }


            System.out.println("Existing status: " + existing.getStatus());
            if(existing.getStatus() == IdempotencyStatus.COMPLETED){
                return new CreateUrlResponse(existing.getShortCode(), "http://localhost:8080/" + existing.getShortCode());
            }


            if(existing.getStatus() == IdempotencyStatus.PROCESSING){
                throw new IdempotencyRequestInProgressException("Request with this idempotency key is already being processed");
            }
        }

        /*
         * We successfully claimed the idempotency key.
         */
        System.out.println("Generating a new URL");

        Long id = snowflakeIdGenerator.generateId();

        String shortCode = shortCodeGenerator.generate();

        Url url = new Url();

        url.setId(id);
        url.setShortCode(shortCode);
        url.setOriginalUrl(request.getOriginalUrl());
        url.setCreatedAt(now);
        url.setActive(true);

        urlRepository.save(url);

        /*
         * Mark idempotency request as completed.
         */

        System.out.println("Marking idempotency request as completed.");
        IdempotencyRequest idempotencyRequest = idempotencyRepository
                        .findByIdempotencyKey(idempotencyKey)
                        .orElseThrow(() -> new IllegalArgumentException("Failed to retrieve idempotency record."));

        idempotencyRequest.setShortCode(shortCode);
        idempotencyRequest.setStatus(IdempotencyStatus.COMPLETED);

        idempotencyRepository.save(idempotencyRequest);
        System.out.println("Idempotency request marked as completed.");

        return new CreateUrlResponse(shortCode, "http://localhost:8080/" + shortCode);
    }
}
