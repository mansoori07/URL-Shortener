package com.shorturl.repository;

import com.shorturl.entity.IdempotencyRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface IdempotencyRequestRepository extends JpaRepository<IdempotencyRequest, Long> {

    Optional<IdempotencyRequest> findByIdempotencyKey(String idempotencyKey);

    @Modifying
    @Query(value = """
        INSERT INTO idempotency_requests
        (
            idempotency_key, 
            request_hash, 
            status, 
            created_at, 
            expires_at
        )
        VALUES
        (
            :key,
            :requestHash,
            'PROCESSING',
            :createdAt,
            :expiresAt
        )
        ON CONFLICT (idempotency_key) 
        DO NOTHING
        """, nativeQuery = true)
    int insertIfAbsent(@Param("key") String key,
                       @Param("requestHash") String requestHash,
                       @Param("createdAt") LocalDateTime createdAt,
                       @Param("expiresAt") LocalDateTime expiresAt);

}
