package com.wallet.core.model;

import java.time.LocalDateTime;

public record IdempotencyRecord(
    String requestHash,
    String responseBody,
    LocalDateTime createdAt
) {}
