package com.arshkapoor.portfolio.controller;

import com.arshkapoor.portfolio.dto.*;
import com.arshkapoor.portfolio.entity.Portfolio;
import com.arshkapoor.portfolio.entity.Stock;
import com.arshkapoor.portfolio.exception.InsufficientSharesException;
import com.arshkapoor.portfolio.exception.PortfolioNotFoundException;
import com.arshkapoor.portfolio.exception.StockNotFoundException;
import com.arshkapoor.portfolio.service.PortfolioService;
import com.arshkapoor.portfolio.service.StockService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Thymeleaf MVC controller — returns view names, NOT JSON.
 *
 * WHY @Controller and not @RestController:
 *   @RestController = @Controller + @ResponseBody, which forces every return
 *   value through a message converter (JSON serialisation).  A plain @Controller
 *   returns a view name that TemplateEngine resolves to an HTML file.
 *
 * URL space is intentionally separate from the REST API:
 *   /portfolios                          → HTML list page
 *   /portfolios/{id}                     → HTML detail page
 *   /portfolios/new                      → HTML create-portfolio form
 *   /portfolios/{id}/transactions/new    → HTML record-transaction form
 *   /api/portfolios                      → JSON (PortfolioController)
 *   /api/portfolios/{id}/summary         → JSON consumed by allocation-chart.js
 */
@Controller
public class DashboardController {

    private final PortfolioService portfolioService;
    private final StockService     stockService;

    /**
     * Constructor injection of both services.
     * StockService is needed to populate the stock dropdown on the transaction form.
     */
    public DashboardController(PortfolioService portfolioService, StockService stockService) {
        this.portfolioService = portfolioService;
        this.stockService     = stockService;
    }

    /** Redirect root to the portfolio list so / works in the browser. */
    @GetMapping("/")
    public String home() {
        return "redirect:/portfolios";
    }

    /**
     * GET /portfolios
     * Builds a lightweight list of all portfolios and passes it to the view.
     * Using PortfolioListItemDto instead of the full entity avoids serialising
     * LAZY collections that aren't needed on a list page.
     */
    @GetMapping("/portfolios")
    public String portfolioList(Model model) {
        List<PortfolioListItemDto> portfolios = portfolioService.getAllPortfolios()
                .stream()
                .map(p -> new PortfolioListItemDto(p.getId(), p.getName(), p.getCreatedAt()))
                .toList();
        model.addAttribute("portfolios", portfolios);
        model.addAttribute("pageTitle", "Portfolios");
        return "portfolios/list";
    }

    /**
     * GET /portfolios/{id}
     * Passes two model objects to the detail view:
     *   portfolio — id, name, createdAt, and the full holdings list with per-position analytics
     *   summary   — currentValue, costBasis, unrealizedGain, unrealizedGainPct, allocation map
     *
     * The allocation map is NOT passed from here to the template — instead the
     * Thymeleaf page loads allocation-chart.js which calls GET /api/portfolios/{id}/summary
     * via fetch() and builds the Chart.js doughnut client-side.
     * This keeps the chart decoupled from the server render cycle.
     *
     * If the id is unknown, redirects to the list with a flash error message.
     *
     * NOTE: Spring MVC prefers the literal segment /portfolios/new over the path
     * variable /portfolios/{id} when routing GET /portfolios/new, so there is no
     * ambiguity between this handler and newPortfolioForm() below.
     */
    @GetMapping("/portfolios/{id}")
    public String portfolioDetail(@PathVariable Long id,
                                  Model model,
                                  RedirectAttributes ra) {
        try {
            PortfolioDetailDto portfolio = portfolioService.getPortfolioDetail(id);
            PortfolioSummaryDto summary  = portfolioService.getPortfolioSummary(id);
            model.addAttribute("portfolio", portfolio);
            model.addAttribute("summary",   summary);
            model.addAttribute("pageTitle", portfolio.name());
            return "portfolios/detail";
        } catch (PortfolioNotFoundException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/portfolios";
        }
    }

    // ── FEATURE 1: Create Portfolio ──────────────────────────────────────────────

    /**
     * GET /portfolios/new
     * Renders the blank create-portfolio form.
     *
     * The model attribute name "portfolioForm" must match th:object="${portfolioForm}"
     * in new.html so Thymeleaf knows which object to bind field errors against.
     *
     * Spring MVC routes GET /portfolios/new here (literal match) rather than to
     * portfolioDetail() (path variable) because literal path segments always have
     * higher specificity than template variables in RequestMappingHandlerMapping.
     */
    @GetMapping("/portfolios/new")
    public String newPortfolioForm(Model model) {
        model.addAttribute("portfolioForm", new CreatePortfolioForm());
        model.addAttribute("pageTitle", "New Portfolio");
        return "portfolios/new";
    }

    /**
     * POST /portfolios
     * Processes the create-portfolio form submission.
     *
     * Post/Redirect/Get (PRG) pattern:
     *   On success → redirect to GET /portfolios so a browser refresh does not
     *                resubmit the form.  Flash attributes survive the redirect.
     *   On failure → re-render the form view directly (no redirect) so the
     *                BindingResult errors are still in model scope for Thymeleaf.
     *
     * IMPORTANT: BindingResult MUST immediately follow the @ModelAttribute parameter.
     * If it comes later or is absent, Spring throws MethodArgumentNotValidException
     * before the method body runs, bypassing our custom error rendering.
     */
    @PostMapping("/portfolios")
    public String createPortfolio(
            @ModelAttribute("portfolioForm") @Valid CreatePortfolioForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes ra) {

        if (bindingResult.hasErrors()) {
            // Re-render the form — BindingResult errors are already in the model
            // via the @ModelAttribute binding; pageTitle is added manually.
            model.addAttribute("pageTitle", "New Portfolio");
            return "portfolios/new";
        }

        Portfolio portfolio = portfolioService.createPortfolio(form.getName());
        // Flash attribute survives the redirect and is consumed once by the list page.
        ra.addFlashAttribute("success",
                "Portfolio \"" + portfolio.getName() + "\" created successfully.");
        return "redirect:/portfolios";
    }

    // ── FEATURE 2: Record Transaction ────────────────────────────────────────────

    /**
     * GET /portfolios/{id}/transactions/new
     * Renders the transaction form with all available stocks in the dropdown.
     *
     * Passing List<Stock> (the JPA entity) directly to the template is safe here
     * because the template only reads id, ticker, and name — no LAZY collections
     * are accessed, and the method runs inside a read-only transaction from the service.
     */
    @GetMapping("/portfolios/{id}/transactions/new")
    public String newTransactionForm(@PathVariable Long id,
                                     Model model,
                                     RedirectAttributes ra) {
        try {
            PortfolioDetailDto portfolio = portfolioService.getPortfolioDetail(id);
            List<Stock>        stocks    = stockService.getAllStocks();
            model.addAttribute("portfolio",       portfolio);
            model.addAttribute("stocks",          stocks);
            model.addAttribute("transactionForm", new TransactionForm());
            model.addAttribute("pageTitle",       "Record Transaction");
            return "portfolios/transaction";
        } catch (PortfolioNotFoundException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/portfolios";
        }
    }

    /**
     * POST /portfolios/{id}/transactions
     * Processes the transaction form submission.
     *
     * Three outcomes:
     *   1. Bean Validation failure (missing/invalid field) → re-render form with errors.
     *   2. InsufficientSharesException (SELL qty > held)  → re-render form with a
     *      friendly message extracted from the exception (not a stack trace).
     *   3. Success → PRG redirect to GET /portfolios/{id} so the page reloads with
     *      updated holdings, recalculated valuation, and a fresh allocation chart.
     *
     * WHY catch InsufficientSharesException here and not in GlobalExceptionHandler:
     *   GlobalExceptionHandler is annotated @RestControllerAdvice, which means it
     *   returns JSON ProblemDetail responses.  A Thymeleaf form needs an HTML response,
     *   so the exception must be caught inside this MVC controller method and handled
     *   by re-rendering the template view.
     *
     * StockNotFoundException is also caught: it could fire if someone crafts a request
     * with a non-existent stockId (e.g. by editing the HTML form manually).
     */
    @PostMapping("/portfolios/{id}/transactions")
    public String recordTransaction(
            @PathVariable Long id,
            @ModelAttribute("transactionForm") @Valid TransactionForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes ra) {

        // Step 1: surface field-level validation errors.
        if (bindingResult.hasErrors()) {
            return reRenderTransactionForm(id, form, model, null, ra);
        }

        // Step 2: attempt the business operation; catch service-layer rule violations.
        try {
            TransactionRequest req = new TransactionRequest(
                    form.getStockId(), form.getType(), form.getQuantity(), form.getPrice());
            portfolioService.applyTransaction(id, req);
            ra.addFlashAttribute("success", "Transaction recorded successfully.");
            return "redirect:/portfolios/" + id;

        } catch (InsufficientSharesException ex) {
            // Business-rule violation: user tried to sell more shares than held.
            // getMessage() returns "Cannot sell N share(s) of TICKER — only M held."
            return reRenderTransactionForm(id, form, model, ex.getMessage(), ra);

        } catch (StockNotFoundException ex) {
            return reRenderTransactionForm(id, form, model,
                    "The selected stock no longer exists.", ra);

        } catch (PortfolioNotFoundException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/portfolios";
        }
    }

    /**
     * Helper: repopulates the transaction form model to avoid duplicating the
     * service/lookup calls in the three error branches of recordTransaction().
     *
     * @param errorMsg  an optional inline error message (null = none); used for
     *                  business-rule violations that don't map to a specific field.
     */
    private String reRenderTransactionForm(Long id, TransactionForm form,
                                            Model model, String errorMsg,
                                            RedirectAttributes ra) {
        try {
            PortfolioDetailDto portfolio = portfolioService.getPortfolioDetail(id);
            List<Stock>        stocks    = stockService.getAllStocks();
            model.addAttribute("portfolio",       portfolio);
            model.addAttribute("stocks",          stocks);
            model.addAttribute("transactionForm", form);   // preserve user's inputs
            model.addAttribute("pageTitle",       "Record Transaction");
            if (errorMsg != null) {
                model.addAttribute("error", errorMsg);
            }
            return "portfolios/transaction";
        } catch (PortfolioNotFoundException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/portfolios";
        }
    }
}

