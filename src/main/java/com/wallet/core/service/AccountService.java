package com.wallet.core.service;

import com.wallet.core.dto.AccountDto;
import com.wallet.core.dto.LedgerEntryDto;
import com.wallet.core.exception.AccountNotFoundException;
import com.wallet.core.exception.InsufficientBalanceException;
import com.wallet.core.model.Account;
import com.wallet.core.model.LedgerEntry;
import com.wallet.core.model.LedgerType;
import com.wallet.core.repository.AccountRepository;
import com.wallet.core.repository.LedgerEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public AccountService(AccountRepository accountRepository, LedgerEntryRepository ledgerEntryRepository) {
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Transactional
    public AccountDto createAccount(String username, BigDecimal initialBalance) {
        Account account = new Account(username, initialBalance);
        Account savedAccount = accountRepository.save(account);
        return mapToDto(savedAccount);
    }

    @Transactional(readOnly = true)
    public AccountDto getAccountById(Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException("Account not found with id: " + id));
        return mapToDto(account);
    }

    @Transactional
    public AccountDto deposit(Long id, BigDecimal amount) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException("Account not found with id: " + id));

        account.setBalance(account.getBalance().add(amount));
        Account updatedAccount = accountRepository.save(account);

        LedgerEntry entry = new LedgerEntry(id, amount, LedgerType.DEPOSIT, LocalDateTime.now());
        ledgerEntryRepository.save(entry);

        return mapToDto(updatedAccount);
    }

    @Transactional
    public AccountDto withdraw(Long id, BigDecimal amount) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException("Account not found with id: " + id));

        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Insufficient balance");
        }

        account.setBalance(account.getBalance().subtract(amount));
        Account updatedAccount = accountRepository.save(account);

        LedgerEntry entry = new LedgerEntry(id, amount, LedgerType.WITHDRAW, LocalDateTime.now());
        ledgerEntryRepository.save(entry);

        return mapToDto(updatedAccount);
    }

    @Transactional
    public void transfer(Long fromId, Long toId, BigDecimal amount) {
        Account fromAccount = accountRepository.findById(fromId)
                .orElseThrow(() -> new AccountNotFoundException("Source account not found with id: " + fromId));

        Account toAccount = accountRepository.findById(toId)
                .orElseThrow(() -> new AccountNotFoundException("Destination account not found with id: " + toId));

        if (fromAccount.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Insufficient balance");
        }

        // Withdraw from source
        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        Account updatedFromAccount = accountRepository.save(fromAccount);

        LedgerEntry fromEntry = new LedgerEntry(fromId, amount, LedgerType.TRANSFER, LocalDateTime.now());
        ledgerEntryRepository.save(fromEntry);

        // Deposit to destination
        toAccount.setBalance(toAccount.getBalance().add(amount));
        Account updatedToAccount = accountRepository.save(toAccount);

        LedgerEntry toEntry = new LedgerEntry(toId, amount, LedgerType.TRANSFER, LocalDateTime.now());
        ledgerEntryRepository.save(toEntry);
    }

    @Transactional(readOnly = true)
    public List<LedgerEntryDto> getLedgerByAccountId(Long id) {
        if (!accountRepository.existsById(id)) {
            throw new AccountNotFoundException("Account not found with id: " + id);
        }
        return ledgerEntryRepository.findAllByAccountIdOrderByCreatedAtAsc(id).stream()
                .map(entry -> new LedgerEntryDto(
                        entry.getId(),
                        entry.getAccountId(),
                        entry.getAmount(),
                        entry.getType(),
                        entry.getCreatedAt()
                ))
                .toList();
    }

    private AccountDto mapToDto(Account account) {
        return new AccountDto(account.getId(), account.getUsername(), account.getBalance());
    }
}
