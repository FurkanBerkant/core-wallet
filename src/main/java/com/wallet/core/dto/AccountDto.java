package com.wallet.core.dto;

import java.math.BigDecimal;

public record AccountDto(
    Long id,
    String username,
    BigDecimal balance
) {
}
