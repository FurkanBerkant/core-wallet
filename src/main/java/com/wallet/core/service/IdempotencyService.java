package com.wallet.core.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.core.exception.IdempotencyConflictException;
import com.wallet.core.model.IdempotencyRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final String KEY_PREFIX = "idempotency:";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public <T> T execute(String key, String requestHash, Class<T> responseType, Supplier<T> operation) {
        if (key == null || key.isBlank()) {
            return operation.get();
        }

        String redisKey = KEY_PREFIX + key;
        Optional<IdempotencyRecord> recordOpt = findRecord(redisKey);

        if (recordOpt.isPresent()) {
            return replay(recordOpt.get(), requestHash, responseType);
        }

        T result = operation.get();
        storeRecord(redisKey, requestHash, result);
        return result;
    }

    public void execute(String key, String requestHash, Runnable operation) {
        execute(key, requestHash, Void.class, () -> {
            operation.run();
            return null;
        });
    }

    private Optional<IdempotencyRecord> findRecord(String redisKey) {
        String json = redisTemplate.opsForValue().get(redisKey);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, IdempotencyRecord.class));
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize idempotency record for key {}", redisKey, e);
            return Optional.empty();
        }
    }

    private <T> T replay(IdempotencyRecord record, String requestHash, Class<T> responseType) {
        if (!record.requestHash().equals(requestHash)) {
            throw new IdempotencyConflictException("Idempotency key was already used for a different request");
        }

        if (responseType == Void.class || record.responseBody() == null) {
            return null;
        }

        try {
            return objectMapper.readValue(record.responseBody(), responseType);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize response body", e);
        }
    }

    private <T> void storeRecord(String redisKey, String requestHash, T response) {
        try {
            String responseBody = (response == null) ? null : objectMapper.writeValueAsString(response);
            IdempotencyRecord record = new IdempotencyRecord(requestHash, responseBody, LocalDateTime.now());
            String json = objectMapper.writeValueAsString(record);
            redisTemplate.opsForValue().set(redisKey, json, TTL);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize idempotency record", e);
        }
    }
}
