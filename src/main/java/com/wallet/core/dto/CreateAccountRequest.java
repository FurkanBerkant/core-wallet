package com.wallet.core.dto;

import java.math.BigDecimal;

public record CreateAccountRequest(
    String username,
    BigDecimal initialBalance
) {
}
