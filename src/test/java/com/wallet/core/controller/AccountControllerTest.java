package com.wallet.core.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.core.dto.CreateAccountRequest;
import com.wallet.core.dto.TransactionRequest;
import com.wallet.core.dto.TransferRequest;
import com.wallet.core.model.Account;
import com.wallet.core.model.LedgerEntry;
import com.wallet.core.model.LedgerType;
import com.wallet.core.repository.AccountRepository;
import com.wallet.core.repository.LedgerEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
})
@AutoConfigureMockMvc
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @org.springframework.boot.test.mock.mockito.MockBean
    private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    private org.springframework.data.redis.core.ValueOperations<String, String> valueOperations;

    @Autowired
    private ObjectMapper objectMapper;

    private final java.util.Map<String, String> redisCache = new java.util.concurrent.ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        redisCache.clear();
        valueOperations = org.mockito.Mockito.mock(org.springframework.data.redis.core.ValueOperations.class);

        org.mockito.Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        org.mockito.Mockito.when(valueOperations.get(org.mockito.Mockito.anyString()))
                .thenAnswer(invocation -> {
                    String key = invocation.getArgument(0);
                    return redisCache.get(key);
                });

        org.mockito.Mockito.doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String value = invocation.getArgument(1);
            redisCache.put(key, value);
            return null;
        }).when(valueOperations).set(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyString(), org.mockito.Mockito.any(java.time.Duration.class));

        ledgerEntryRepository.deleteAll();
        accountRepository.deleteAll();
    }


    @Test
    void shouldCreateAccountSuccessfully() throws Exception {
        CreateAccountRequest request = new CreateAccountRequest("john_doe", new BigDecimal("150.00"));

        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.username", is("john_doe")))
                .andExpect(jsonPath("$.balance", is(150.00)));
    }

    @Test
    void shouldGetAccountByIdSuccessfully() throws Exception {
        Account account = new Account("jane_doe", new BigDecimal("200.50"));
        Account savedAccount = accountRepository.save(account);

        mockMvc.perform(get("/api/accounts/{id}", savedAccount.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(savedAccount.getId().intValue())))
                .andExpect(jsonPath("$.username", is("jane_doe")))
                .andExpect(jsonPath("$.balance", is(200.50)));
    }

    @Test
    void shouldReturn404WhenAccountNotFound() throws Exception {
        mockMvc.perform(get("/api/accounts/{id}", 999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldDepositSuccessfully() throws Exception {
        Account account = new Account("deposit_user", new BigDecimal("100.00"));
        Account savedAccount = accountRepository.save(account);

        TransactionRequest request = new TransactionRequest(new BigDecimal("50.00"));

        mockMvc.perform(post("/api/accounts/{id}/deposit", savedAccount.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(savedAccount.getId().intValue())))
                .andExpect(jsonPath("$.username", is("deposit_user")))
                .andExpect(jsonPath("$.balance", is(150.00)));

        List<LedgerEntry> entries = ledgerEntryRepository.findAll();
        assertEquals(1, entries.size());
        LedgerEntry entry = entries.get(0);
        assertEquals(savedAccount.getId(), entry.getAccountId());
        assertEquals(0, new BigDecimal("50.00").compareTo(entry.getAmount()));
        assertEquals(LedgerType.DEPOSIT, entry.getType());
    }

    @Test
    void shouldReplayDepositWhenIdempotencyKeyIsReused() throws Exception {
        Account account = new Account("idempotent_deposit_user", new BigDecimal("100.00"));
        Account savedAccount = accountRepository.save(account);

        TransactionRequest request = new TransactionRequest(new BigDecimal("50.00"));

        mockMvc.perform(post("/api/accounts/{id}/deposit", savedAccount.getId())
                        .header("Idempotency-Key", "deposit-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", is(150.00)));

        mockMvc.perform(post("/api/accounts/{id}/deposit", savedAccount.getId())
                        .header("Idempotency-Key", "deposit-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", is(150.00)));

        Account updatedAccount = accountRepository.findById(savedAccount.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("150.00").compareTo(updatedAccount.getBalance()));
        assertEquals(1, ledgerEntryRepository.findAll().size());
    }

    @Test
    void shouldReturn409WhenIdempotencyKeyIsReusedForDifferentRequest() throws Exception {
        Account account = new Account("idempotency_conflict_user", new BigDecimal("100.00"));
        Account savedAccount = accountRepository.save(account);

        mockMvc.perform(post("/api/accounts/{id}/deposit", savedAccount.getId())
                        .header("Idempotency-Key", "same-key-different-body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TransactionRequest(new BigDecimal("50.00")))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/accounts/{id}/deposit", savedAccount.getId())
                        .header("Idempotency-Key", "same-key-different-body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TransactionRequest(new BigDecimal("60.00")))))
                .andExpect(status().isConflict());

        Account updatedAccount = accountRepository.findById(savedAccount.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("150.00").compareTo(updatedAccount.getBalance()));
        assertEquals(1, ledgerEntryRepository.findAll().size());
    }

    @Test
    void shouldWithdrawSuccessfully() throws Exception {
        Account account = new Account("withdraw_user", new BigDecimal("100.00"));
        Account savedAccount = accountRepository.save(account);

        TransactionRequest request = new TransactionRequest(new BigDecimal("40.00"));

        mockMvc.perform(post("/api/accounts/{id}/withdraw", savedAccount.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(savedAccount.getId().intValue())))
                .andExpect(jsonPath("$.username", is("withdraw_user")))
                .andExpect(jsonPath("$.balance", is(60.00)));

        List<LedgerEntry> entries = ledgerEntryRepository.findAll();
        assertEquals(1, entries.size());
        LedgerEntry entry = entries.get(0);
        assertEquals(savedAccount.getId(), entry.getAccountId());
        assertEquals(0, new BigDecimal("40.00").compareTo(entry.getAmount()));
        assertEquals(LedgerType.WITHDRAW, entry.getType());
    }

    @Test
    void shouldReturn400WhenWithdrawAmountExceedsBalance() throws Exception {
        Account account = new Account("withdraw_poor_user", new BigDecimal("10.00"));
        Account savedAccount = accountRepository.save(account);

        TransactionRequest request = new TransactionRequest(new BigDecimal("15.00"));

        mockMvc.perform(post("/api/accounts/{id}/withdraw", savedAccount.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        List<LedgerEntry> entries = ledgerEntryRepository.findAll();
        assertEquals(0, entries.size());
    }

    @Test
    void shouldTransferSuccessfully() throws Exception {
        Account fromAccount = accountRepository.save(new Account("from_user", new BigDecimal("200.00")));
        Account toAccount = accountRepository.save(new Account("to_user", new BigDecimal("50.00")));

        TransferRequest request = new TransferRequest(fromAccount.getId(), toAccount.getId(), new BigDecimal("75.00"));

        mockMvc.perform(post("/api/accounts/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Verify balances
        Account updatedFrom = accountRepository.findById(fromAccount.getId()).orElseThrow();
        Account updatedTo = accountRepository.findById(toAccount.getId()).orElseThrow();

        assertEquals(0, new BigDecimal("125.00").compareTo(updatedFrom.getBalance()));
        assertEquals(0, new BigDecimal("125.00").compareTo(updatedTo.getBalance()));

        // Verify ledger entries
        List<LedgerEntry> entries = ledgerEntryRepository.findAll();
        assertEquals(2, entries.size());

        // Find transfer entry for source
        LedgerEntry fromEntry = entries.stream()
                .filter(e -> e.getAccountId().equals(fromAccount.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals(0, new BigDecimal("75.00").compareTo(fromEntry.getAmount()));
        assertEquals(LedgerType.TRANSFER, fromEntry.getType());

        // Find transfer entry for destination
        LedgerEntry toEntry = entries.stream()
                .filter(e -> e.getAccountId().equals(toAccount.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals(0, new BigDecimal("75.00").compareTo(toEntry.getAmount()));
        assertEquals(LedgerType.TRANSFER, toEntry.getType());
    }

    @Test
    void shouldReplayTransferWhenIdempotencyKeyIsReused() throws Exception {
        Account fromAccount = accountRepository.save(new Account("idempotent_from_user", new BigDecimal("200.00")));
        Account toAccount = accountRepository.save(new Account("idempotent_to_user", new BigDecimal("50.00")));

        TransferRequest request = new TransferRequest(fromAccount.getId(), toAccount.getId(), new BigDecimal("75.00"));

        mockMvc.perform(post("/api/accounts/transfer")
                        .header("Idempotency-Key", "transfer-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/accounts/transfer")
                        .header("Idempotency-Key", "transfer-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        Account updatedFrom = accountRepository.findById(fromAccount.getId()).orElseThrow();
        Account updatedTo = accountRepository.findById(toAccount.getId()).orElseThrow();

        assertEquals(0, new BigDecimal("125.00").compareTo(updatedFrom.getBalance()));
        assertEquals(0, new BigDecimal("125.00").compareTo(updatedTo.getBalance()));
        assertEquals(2, ledgerEntryRepository.findAll().size());
    }

    @Test
    void shouldReturn400WhenTransferAmountExceedsBalance() throws Exception {
        Account fromAccount = accountRepository.save(new Account("poor_from_user", new BigDecimal("30.00")));
        Account toAccount = accountRepository.save(new Account("to_user", new BigDecimal("50.00")));

        TransferRequest request = new TransferRequest(fromAccount.getId(), toAccount.getId(), new BigDecimal("40.00"));

        mockMvc.perform(post("/api/accounts/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        // Verify balances are unchanged
        Account unchangedFrom = accountRepository.findById(fromAccount.getId()).orElseThrow();
        Account unchangedTo = accountRepository.findById(toAccount.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("30.00").compareTo(unchangedFrom.getBalance()));
        assertEquals(0, new BigDecimal("50.00").compareTo(unchangedTo.getBalance()));

        // Verify no ledger entries are created
        List<LedgerEntry> entries = ledgerEntryRepository.findAll();
        assertEquals(0, entries.size());
    }

    @Test
    void shouldReturn404WhenTransferAccountNotFound() throws Exception {
        Account existing = accountRepository.save(new Account("existing", new BigDecimal("100.00")));

        TransferRequest request1 = new TransferRequest(999L, existing.getId(), new BigDecimal("10.00"));
        mockMvc.perform(post("/api/accounts/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isNotFound());

        TransferRequest request2 = new TransferRequest(existing.getId(), 999L, new BigDecimal("10.00"));
        mockMvc.perform(post("/api/accounts/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldGetLedgerSuccessfully() throws Exception {
        Account account = accountRepository.save(new Account("ledger_user", new BigDecimal("100.00")));

        // Perform some transactions (deposit and withdraw) using the MockMvc or directly saving to repository to control timestamps
        // MockMvc ensures they are created sequentially
        TransactionRequest depositReq = new TransactionRequest(new BigDecimal("50.00"));
        mockMvc.perform(post("/api/accounts/{id}/deposit", account.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(depositReq)))
                .andExpect(status().isOk());

        TransactionRequest withdrawReq = new TransactionRequest(new BigDecimal("30.00"));
        mockMvc.perform(post("/api/accounts/{id}/withdraw", account.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(withdrawReq)))
                .andExpect(status().isOk());

        // Call the get ledger history endpoint
        mockMvc.perform(get("/api/accounts/{id}/ledger", account.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                // Chronological sorting asserts DEPOSIT is index 0
                .andExpect(jsonPath("$[0].accountId", is(account.getId().intValue())))
                .andExpect(jsonPath("$[0].amount", is(50.00)))
                .andExpect(jsonPath("$[0].type", is("DEPOSIT")))
                .andExpect(jsonPath("$[0].createdAt", notNullValue()))
                // WITHDRAW is index 1
                .andExpect(jsonPath("$[1].accountId", is(account.getId().intValue())))
                .andExpect(jsonPath("$[1].amount", is(30.00)))
                .andExpect(jsonPath("$[1].type", is("WITHDRAW")))
                .andExpect(jsonPath("$[1].createdAt", notNullValue()));
    }

    @Test
    void shouldReturn404WhenGetLedgerAccountNotFound() throws Exception {
        mockMvc.perform(get("/api/accounts/{id}/ledger", 999L))
                .andExpect(status().isNotFound());
    }
}
