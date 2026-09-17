package com.shorturl.service;

import com.shorturl.entity.IdempotencyRequest;
import com.shorturl.entity.IdempotencyStatus;
import com.shorturl.exception.IdempotencyKeyConflictException;
import com.shorturl.exception.IdempotencyRequestInProgressException;
import com.shorturl.repository.IdempotencyRequestRepository;
import com.shorturl.util.HashUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.TimeZone;

@Service
public class IdempotencyService {

    private static final long EXPIRY_HOURS = 24;
    private final IdempotencyRequestRepository repository;
    private final HashUtil hashUtil;

    public IdempotencyService(IdempotencyRequestRepository repository, HashUtil hashUtil){
        this.repository = repository;
        this.hashUtil = hashUtil;
    }

    public Optional<IdempotencyRequest> findExisting(String idempotencyKey){
        return repository.findByIdempotencyKey(idempotencyKey);
    }

    public IdempotencyRequest createProcessingRecord(String idempotencyKey, String originalUrl){
        String requestHash = hashUtil.sha256(originalUrl);

        IdempotencyRequest request = new IdempotencyRequest();

        request.setIdempotencyKey(idempotencyKey);
        request.setRequestHash(requestHash);
        request.setStatus(IdempotencyStatus.PROCESSING);
        request.setCreatedAt(LocalDateTime.now());
        request.setExpiresAt(LocalDateTime.now().plusHours(EXPIRY_HOURS));

        try{
            return repository.saveAndFlush(request);
        } catch (Exception e){
            return repository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new IllegalArgumentException("Failed to create or retrieve idempotency record for key: " + idempotencyKey));
        }
    }

    public void validRequest(IdempotencyRequest existing, String originalUrl){
        String requestHash = hashUtil.sha256(originalUrl);

        if(!existing.getRequestHash().equals(requestHash)){
            throw new IdempotencyKeyConflictException("Idempotency key was already used with a different request payload. Key: " + existing.getIdempotencyKey());
        }

        if(existing.getStatus() == IdempotencyStatus.PROCESSING){
            throw new IdempotencyRequestInProgressException("Request has already been processed for idempotency key: " + existing.getIdempotencyKey());
        }

    }

    public void markCompleted(IdempotencyRequest request, String shortCode) {
        request.setShortCode(shortCode);
        request.setStatus(IdempotencyStatus.COMPLETED);

        repository.save(request);
    }

    public void markFailed(IdempotencyRequest request) {
        request.setStatus(IdempotencyStatus.FAILED);
        repository.save(request);
    }

}
