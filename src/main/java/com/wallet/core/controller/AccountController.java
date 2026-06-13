package com.wallet.core.controller;

import com.wallet.core.dto.AccountDto;
import com.wallet.core.dto.CreateAccountRequest;
import com.wallet.core.dto.LedgerEntryDto;
import com.wallet.core.dto.TransactionRequest;
import com.wallet.core.dto.TransferRequest;
import com.wallet.core.service.AccountService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountDto createAccount(@RequestBody CreateAccountRequest request) {
        return accountService.createAccount(request.username(), request.initialBalance());
    }

    @GetMapping("/{id}")
    public AccountDto getAccount(@PathVariable Long id) {
        return accountService.getAccountById(id);
    }

    @PostMapping("/{id}/deposit")
    public AccountDto deposit(@PathVariable Long id, @RequestBody TransactionRequest request) {
        return accountService.deposit(id, request.amount());
    }

    @PostMapping("/{id}/withdraw")
    public AccountDto withdraw(@PathVariable Long id, @RequestBody TransactionRequest request) {
        return accountService.withdraw(id, request.amount());
    }

    @PostMapping("/transfer")
    @ResponseStatus(HttpStatus.OK)
    public void transfer(@RequestBody TransferRequest request) {
        accountService.transfer(request.fromId(), request.toId(), request.amount());
    }

    @GetMapping("/{id}/ledger")
    public List<LedgerEntryDto> getLedger(@PathVariable Long id) {
        return accountService.getLedgerByAccountId(id);
    }
}
