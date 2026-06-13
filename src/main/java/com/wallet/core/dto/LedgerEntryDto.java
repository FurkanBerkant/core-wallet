package com.wallet.core.dto;

import com.wallet.core.model.LedgerType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LedgerEntryDto(
    Long id,
    Long accountId,
    BigDecimal amount,
    LedgerType type,
    LocalDateTime createdAt
) {
}
