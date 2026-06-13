package com.wallet.core.repository;

import com.wallet.core.model.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
    List<LedgerEntry> findAllByAccountIdOrderByCreatedAtAsc(Long accountId);
}
