package com.arshkapoor.portfolio.service;

import com.arshkapoor.portfolio.dto.PortfolioSummaryDto;
import com.arshkapoor.portfolio.dto.TransactionRequest;
import com.arshkapoor.portfolio.dto.TransactionResponseDto;
import com.arshkapoor.portfolio.entity.*;
import com.arshkapoor.portfolio.exception.InsufficientSharesException;
import com.arshkapoor.portfolio.exception.PortfolioNotFoundException;
import com.arshkapoor.portfolio.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for PortfolioService — no Spring context, no database.
 *
 * @ExtendWith(MockitoExtension.class): activates Mockito annotations
 * (@Mock, @InjectMocks) without needing a JUnit 4 Runner or @SpringBootTest.
 * Each test runs in milliseconds because nothing is wired or started.
 *
 * @InjectMocks: Mockito constructs PortfolioService via its 4-arg constructor,
 * injecting the four @Mock objects.  This is constructor injection, which is
 * why the service was intentionally written without @Autowired — it makes
 * Mockito injection work without any Spring magic.
 *
 * Strategy: stub only the repository methods each specific test needs.
 * Mockito throws a clear error if an unstubbed method is called unexpectedly
 * (strict stubbing is the default with MockitoExtension).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PortfolioService")
class PortfolioServiceTest {

    // ── Mocked dependencies ───────────────────────────────────────────────────

    @Mock PortfolioRepository  portfolioRepo;
    @Mock HoldingRepository    holdingRepo;
    @Mock TransactionRepository transactionRepo;
    @Mock StockRepository      stockRepo;

    /** The class under test — repositories injected via constructor by Mockito. */
    @InjectMocks
    PortfolioService portfolioService;

    // ── Shared test fixtures ──────────────────────────────────────────────────

    private static final Long PORTFOLIO_ID = 1L;
    private static final Long AAPL_ID      = 10L;
    private static final Long MSFT_ID      = 20L;

    private Stock     aapl;    // current price $190, cost basis $150 → gain
    private Stock     msft;    // current price $400, cost basis $320
    private Portfolio portfolio;

    /**
     * @BeforeEach recreates the test fixtures before every test method.
     * This guarantees tests are independent: mutating aapl in one test
     * cannot bleed into another.
     */
    @BeforeEach
    void setUp() {
        aapl      = new Stock("AAPL", "Apple Inc.",     new BigDecimal("190.00"), "Technology");
        msft      = new Stock("MSFT", "Microsoft Corp", new BigDecimal("400.00"), "Technology");
        portfolio = new Portfolio("Test Portfolio");
    }

    // =========================================================================
    // currentValue
    // =========================================================================

    @Nested
    @DisplayName("currentValue()")
    class CurrentValue {

        @Test
        @DisplayName("returns ZERO for a portfolio with no holdings")
        void emptyPortfolio_returnsZero() {
            when(portfolioRepo.existsById(PORTFOLIO_ID)).thenReturn(true);
            when(holdingRepo.findByPortfolioIdWithStock(PORTFOLIO_ID)).thenReturn(List.of());

            BigDecimal result = portfolioService.currentValue(PORTFOLIO_ID);

            // compareTo(ZERO) == 0 handles scale differences: 0.0000 == 0
            assertThat(result.compareTo(BigDecimal.ZERO)).isZero();
        }

        @Test
        @DisplayName("sums qty × currentPrice across all holdings correctly")
        void multipleHoldings_computesCorrectSum() {
            // AAPL: 10 × $190 = $1 900
            // MSFT:  5 × $400 = $2 000
            // Expected total  = $3 900
            Holding hAapl = new Holding(aapl, 10, new BigDecimal("150.00"));
            Holding hMsft = new Holding(msft,  5, new BigDecimal("320.00"));

            when(portfolioRepo.existsById(PORTFOLIO_ID)).thenReturn(true);
            when(holdingRepo.findByPortfolioIdWithStock(PORTFOLIO_ID))
                    .thenReturn(List.of(hAapl, hMsft));

            assertThat(portfolioService.currentValue(PORTFOLIO_ID))
                    .isEqualByComparingTo(new BigDecimal("3900"));
        }

        @Test
        @DisplayName("throws PortfolioNotFoundException for an unknown id")
        void unknownPortfolio_throwsNotFoundException() {
            when(portfolioRepo.existsById(99L)).thenReturn(false);

            assertThatThrownBy(() -> portfolioService.currentValue(99L))
                    .isInstanceOf(PortfolioNotFoundException.class)
                    .hasMessageContaining("99");
        }
    }

    // =========================================================================
    // costBasis
    // =========================================================================

    @Nested
    @DisplayName("costBasis()")
    class CostBasis {

        @Test
        @DisplayName("sums qty × avgBuyPrice — ignores current market price")
        void multipleHoldings_computesCorrectCostBasis() {
            // AAPL: 10 × $150 = $1 500
            // MSFT:  5 × $320 = $1 600
            // Expected total  = $3 100
            Holding hAapl = new Holding(aapl, 10, new BigDecimal("150.00"));
            Holding hMsft = new Holding(msft,  5, new BigDecimal("320.00"));

            when(portfolioRepo.existsById(PORTFOLIO_ID)).thenReturn(true);
            when(holdingRepo.findByPortfolioId(PORTFOLIO_ID))
                    .thenReturn(List.of(hAapl, hMsft));

            assertThat(portfolioService.costBasis(PORTFOLIO_ID))
                    .isEqualByComparingTo(new BigDecimal("3100"));
        }
    }

    // =========================================================================
    // unrealizedGain
    // =========================================================================

    @Nested
    @DisplayName("unrealizedGain()")
    class UnrealizedGain {

        /**
         * Both currentValue() and costBasis() call ensureExists() → existsById().
         * We stub it once; Mockito will return true for every call in this test.
         */
        private void stubHoldings(Holding... holdings) {
            when(portfolioRepo.existsById(PORTFOLIO_ID)).thenReturn(true);
            when(holdingRepo.findByPortfolioIdWithStock(PORTFOLIO_ID))
                    .thenReturn(List.of(holdings));
            when(holdingRepo.findByPortfolioId(PORTFOLIO_ID))
                    .thenReturn(List.of(holdings));
        }

        @Test
        @DisplayName("is POSITIVE when current price > cost basis (prices rose)")
        void positive_whenPricesUp() {
            // Bought AAPL at $150, now worth $190 → $40 gain × 10 shares = $400
            stubHoldings(new Holding(aapl, 10, new BigDecimal("150.00")));

            BigDecimal gain = portfolioService.unrealizedGain(PORTFOLIO_ID);

            assertThat(gain).isGreaterThan(BigDecimal.ZERO);
            // currentValue = 1 900, costBasis = 1 500, gain = 400
            assertThat(gain).isEqualByComparingTo(new BigDecimal("400"));
        }

        @Test
        @DisplayName("is NEGATIVE when current price < cost basis (prices fell)")
        void negative_whenPricesDown() {
            // Bought MSFT at $450, now worth $400 → -$50 loss × 5 shares = -$250
            Holding h = new Holding(msft, 5, new BigDecimal("450.00"));
            stubHoldings(h);

            BigDecimal gain = portfolioService.unrealizedGain(PORTFOLIO_ID);

            assertThat(gain).isLessThan(BigDecimal.ZERO);
            // currentValue = 2 000, costBasis = 2 250, gain = -250
            assertThat(gain).isEqualByComparingTo(new BigDecimal("-250"));
        }
    }

    // =========================================================================
    // allocation
    // =========================================================================

    @Nested
    @DisplayName("allocation()")
    class Allocation {

        @Test
        @DisplayName("returns an empty map for an empty portfolio — no division by zero")
        void emptyPortfolio_returnsEmptyMapWithoutException() {
            when(holdingRepo.findByPortfolioIdWithStock(PORTFOLIO_ID))
                    .thenReturn(List.of());

            // assertThatNoException ensures the empty-portfolio guard works
            assertThatNoException().isThrownBy(() -> {
                Map<String, BigDecimal> result = portfolioService.allocation(PORTFOLIO_ID);
                assertThat(result).isEmpty();
            });
        }

        @Test
        @DisplayName("percentages sum to ~100 for a multi-holding portfolio")
        void multipleHoldings_percentagesSumToOneHundred() {
            // AAPL: 10 × $190 = $1 900  →  48.72 %
            // MSFT:  5 × $400 = $2 000  →  51.28 %
            // Total            = $3 900  → 100.00 %
            when(holdingRepo.findByPortfolioIdWithStock(PORTFOLIO_ID))
                    .thenReturn(List.of(
                            new Holding(aapl, 10, new BigDecimal("150.00")),
                            new Holding(msft,  5, new BigDecimal("320.00"))
                    ));

            Map<String, BigDecimal> alloc = portfolioService.allocation(PORTFOLIO_ID);

            assertThat(alloc).containsKeys("AAPL", "MSFT");
            assertThat(alloc.values()).allSatisfy(pct -> assertThat(pct).isPositive());

            BigDecimal sum = alloc.values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            // 48.72 + 51.28 = 100.00 exactly for these numbers
            assertThat(sum).isEqualByComparingTo(new BigDecimal("100.00"));
        }

        @Test
        @DisplayName("single holding gets 100% allocation")
        void singleHolding_getsFullAllocation() {
            when(holdingRepo.findByPortfolioIdWithStock(PORTFOLIO_ID))
                    .thenReturn(List.of(new Holding(aapl, 10, new BigDecimal("150.00"))));

            Map<String, BigDecimal> alloc = portfolioService.allocation(PORTFOLIO_ID);

            assertThat(alloc.get("AAPL")).isEqualByComparingTo(new BigDecimal("100.00"));
        }
    }

    // =========================================================================
    // applyTransaction — BUY path
    // =========================================================================

    @Nested
    @DisplayName("applyTransaction() — BUY")
    class ApplyTransactionBuy {

        @Test
        @DisplayName("creates a new Holding when no position exists yet")
        void noExistingHolding_createsNewHolding() {
            stubPortfolioAndStock(AAPL_ID, aapl);
            when(holdingRepo.findByPortfolioIdAndStockId(PORTFOLIO_ID, AAPL_ID))
                    .thenReturn(Optional.empty());
            when(holdingRepo.save(any(Holding.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(transactionRepo.save(any(Transaction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            portfolioService.applyTransaction(PORTFOLIO_ID,
                    new TransactionRequest(AAPL_ID, TransactionType.BUY, 10,
                            new BigDecimal("150.00")));

            ArgumentCaptor<Holding> captor = ArgumentCaptor.forClass(Holding.class);
            verify(holdingRepo).save(captor.capture());
            Holding saved = captor.getValue();
            assertThat(saved.getQuantity()).isEqualTo(10);
            assertThat(saved.getAvgBuyPrice()).isEqualByComparingTo(new BigDecimal("150.00"));
            assertThat(saved.getStock()).isEqualTo(aapl);
        }

        @Test
        @DisplayName("computes weighted-average buy price after two trades at different prices")
        void existingHolding_computesWeightedAveragePrice() {
            // Existing: 10 shares @ $150  → cost $1 500
            // New buy:   5 shares @ $200  → cost $1 000
            // Total:    15 shares,          cost $2 500
            // New avg = $2 500 / 15 = $166.6667  (HALF_UP, scale 4)
            Holding existing = new Holding(aapl, 10, new BigDecimal("150.00"));

            stubPortfolioAndStock(AAPL_ID, aapl);
            when(holdingRepo.findByPortfolioIdAndStockId(PORTFOLIO_ID, AAPL_ID))
                    .thenReturn(Optional.of(existing));
            when(holdingRepo.save(any(Holding.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(transactionRepo.save(any(Transaction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            portfolioService.applyTransaction(PORTFOLIO_ID,
                    new TransactionRequest(AAPL_ID, TransactionType.BUY, 5,
                            new BigDecimal("200.00")));

            ArgumentCaptor<Holding> captor = ArgumentCaptor.forClass(Holding.class);
            verify(holdingRepo).save(captor.capture());
            Holding updated = captor.getValue();

            assertThat(updated.getQuantity()).isEqualTo(15);
            // (10 × 150 + 5 × 200) / 15 = 2500 / 15 = 166.6666… → 166.6667 HALF_UP
            assertThat(updated.getAvgBuyPrice())
                    .isEqualByComparingTo(new BigDecimal("166.6667"));
        }

        @Test
        @DisplayName("always persists an audit Transaction record")
        void alwaysSavesTransactionRecord() {
            stubPortfolioAndStock(AAPL_ID, aapl);
            when(holdingRepo.findByPortfolioIdAndStockId(PORTFOLIO_ID, AAPL_ID))
                    .thenReturn(Optional.empty());
            when(holdingRepo.save(any(Holding.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(transactionRepo.save(any(Transaction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // applyTransaction now returns TransactionResponseDto (not Transaction entity)
            TransactionResponseDto result = portfolioService.applyTransaction(PORTFOLIO_ID,
                    new TransactionRequest(AAPL_ID, TransactionType.BUY, 10,
                            new BigDecimal("150.00")));

            verify(transactionRepo, times(1)).save(any(Transaction.class));
            assertThat(result.type()).isEqualTo(TransactionType.BUY);
            assertThat(result.ticker()).isEqualTo("AAPL");
        }
    }

    // =========================================================================
    // applyTransaction — SELL path
    // =========================================================================

    @Nested
    @DisplayName("applyTransaction() — SELL")
    class ApplyTransactionSell {

        @Test
        @DisplayName("reduces holding quantity by the sold amount")
        void partialSell_reducesQuantity() {
            // Hold 10, sell 3 → 7 remain
            Holding existing = new Holding(aapl, 10, new BigDecimal("150.00"));

            stubPortfolioAndStock(AAPL_ID, aapl);
            when(holdingRepo.findByPortfolioIdAndStockId(PORTFOLIO_ID, AAPL_ID))
                    .thenReturn(Optional.of(existing));
            when(holdingRepo.save(any(Holding.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(transactionRepo.save(any(Transaction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            portfolioService.applyTransaction(PORTFOLIO_ID,
                    new TransactionRequest(AAPL_ID, TransactionType.SELL, 3,
                            new BigDecimal("190.00")));

            ArgumentCaptor<Holding> captor = ArgumentCaptor.forClass(Holding.class);
            verify(holdingRepo).save(captor.capture());
            assertThat(captor.getValue().getQuantity()).isEqualTo(7);
        }

        @Test
        @DisplayName("deletes the Holding row when all shares are sold (qty reaches 0)")
        void fullSell_deletesHolding() {
            Holding existing = new Holding(aapl, 10, new BigDecimal("150.00"));

            stubPortfolioAndStock(AAPL_ID, aapl);
            when(holdingRepo.findByPortfolioIdAndStockId(PORTFOLIO_ID, AAPL_ID))
                    .thenReturn(Optional.of(existing));
            when(transactionRepo.save(any(Transaction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            portfolioService.applyTransaction(PORTFOLIO_ID,
                    new TransactionRequest(AAPL_ID, TransactionType.SELL, 10,
                            new BigDecimal("190.00")));

            verify(holdingRepo).delete(existing);           // DELETE was issued
            verify(holdingRepo, never()).save(any());        // no UPDATE
        }

        @Test
        @DisplayName("throws InsufficientSharesException when selling MORE than held")
        void moreThanHeld_throwsInsufficientSharesException() {
            // Hold 5, attempt to sell 10
            Holding existing = new Holding(aapl, 5, new BigDecimal("150.00"));

            stubPortfolioAndStock(AAPL_ID, aapl);
            when(holdingRepo.findByPortfolioIdAndStockId(PORTFOLIO_ID, AAPL_ID))
                    .thenReturn(Optional.of(existing));

            assertThatThrownBy(() ->
                    portfolioService.applyTransaction(PORTFOLIO_ID,
                            new TransactionRequest(AAPL_ID, TransactionType.SELL, 10,
                                    new BigDecimal("190.00")))
            )
                    .isInstanceOf(InsufficientSharesException.class)
                    .hasMessageContaining("AAPL")
                    .satisfies(ex -> {
                        InsufficientSharesException ise = (InsufficientSharesException) ex;
                        assertThat(ise.getRequested()).isEqualTo(10);
                        assertThat(ise.getHeld()).isEqualTo(5);
                    });

            // Verify NO repository writes were made — fail-fast design
            verify(holdingRepo, never()).save(any());
            verify(holdingRepo, never()).delete(any());
            verify(transactionRepo, never()).save(any());
        }

        @Test
        @DisplayName("throws InsufficientSharesException with held=0 when no position exists")
        void noHoldingExists_throwsWithHeldZero() {
            stubPortfolioAndStock(AAPL_ID, aapl);
            when(holdingRepo.findByPortfolioIdAndStockId(PORTFOLIO_ID, AAPL_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    portfolioService.applyTransaction(PORTFOLIO_ID,
                            new TransactionRequest(AAPL_ID, TransactionType.SELL, 5,
                                    new BigDecimal("190.00")))
            )
                    .isInstanceOf(InsufficientSharesException.class)
                    .satisfies(ex -> {
                        InsufficientSharesException ise = (InsufficientSharesException) ex;
                        assertThat(ise.getHeld()).isZero();       // no position = 0 held
                        assertThat(ise.getTicker()).isEqualTo("AAPL");
                    });
        }
    }

    // =========================================================================
    // getPortfolioSummary — single-pass correctness
    // =========================================================================

    @Nested
    @DisplayName("getPortfolioSummary()")
    class GetPortfolioSummary {

        @Test
        @DisplayName("returns zeroes and empty map for an empty portfolio")
        void emptyPortfolio_returnsZeroSummary() {
            when(portfolioRepo.findById(PORTFOLIO_ID)).thenReturn(Optional.of(portfolio));
            when(holdingRepo.findByPortfolioIdWithStock(PORTFOLIO_ID)).thenReturn(List.of());

            PortfolioSummaryDto dto = portfolioService.getPortfolioSummary(PORTFOLIO_ID);

            assertThat(dto.currentValue()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(dto.costBasis()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(dto.unrealizedGain()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(dto.allocation()).isEmpty();
        }

        @Test
        @DisplayName("computes all five values correctly in a single call")
        void multipleHoldings_allValuesCorrect() {
            // AAPL: 10 × $190 (current) / $150 (cost) →  value $1900,  cost $1500
            // MSFT:  5 × $400 (current) / $320 (cost) →  value $2000,  cost $1600
            //                                     total    value $3900,  cost $3100
            //                              unrealised gain $3900 - $3100 = $800
            //                                  gain %      800 / 3100 × 100 ≈ 25.81%
            when(portfolioRepo.findById(PORTFOLIO_ID)).thenReturn(Optional.of(portfolio));
            when(holdingRepo.findByPortfolioIdWithStock(PORTFOLIO_ID))
                    .thenReturn(List.of(
                            new Holding(aapl, 10, new BigDecimal("150.00")),
                            new Holding(msft,  5, new BigDecimal("320.00"))
                    ));

            PortfolioSummaryDto dto = portfolioService.getPortfolioSummary(PORTFOLIO_ID);

            assertThat(dto.portfolioName()).isEqualTo("Test Portfolio");
            assertThat(dto.currentValue()).isEqualByComparingTo(new BigDecimal("3900.00"));
            assertThat(dto.costBasis()).isEqualByComparingTo(new BigDecimal("3100.00"));
            assertThat(dto.unrealizedGain()).isEqualByComparingTo(new BigDecimal("800.00"));
            assertThat(dto.unrealizedGainPct()).isGreaterThan(BigDecimal.ZERO);
            assertThat(dto.allocation()).containsKeys("AAPL", "MSFT");

            // Verify the repository was only called ONCE — single-pass design
            verify(holdingRepo, times(1)).findByPortfolioIdWithStock(PORTFOLIO_ID);
        }
    }

    // =========================================================================
    // Private helper
    // =========================================================================

    /**
     * Stubs portfolioRepo.findById and stockRepo.findById together — both are
     * always needed at the top of applyTransaction().
     */
    private void stubPortfolioAndStock(Long stockId, Stock stock) {
        when(portfolioRepo.findById(PORTFOLIO_ID)).thenReturn(Optional.of(portfolio));
        when(stockRepo.findById(stockId)).thenReturn(Optional.of(stock));
    }
}

