package com.wallet.core.dto;

import java.math.BigDecimal;

public record TransactionRequest(
    BigDecimal amount
) {
}
