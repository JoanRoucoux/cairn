package com.roucoux.cairn.cucumber;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.roucoux.cairn.generated.model.AccountAllocationResponse;
import com.roucoux.cairn.generated.model.AccountResponse;
import com.roucoux.cairn.generated.model.AccountType;
import com.roucoux.cairn.generated.model.AssetClassAllocationResponse;
import com.roucoux.cairn.generated.model.CreateAccountRequest;
import com.roucoux.cairn.generated.model.CreateHoldingRequest;
import com.roucoux.cairn.generated.model.PerformanceResponse;
import com.roucoux.cairn.generated.model.PortfolioResponse;
import com.roucoux.cairn.generated.model.RecordQuoteRequest;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.jdbc.core.JdbcOperations;

public class PortfolioSteps {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcOperations jdbc;

    private UUID accountId;
    private UUID instrumentId;
    private PortfolioResponse portfolio;
    private AssetClassAllocationResponse classAllocation;
    private AccountAllocationResponse accountAllocation;

    @Before
    public void resetPortfolio() {
        jdbc.update("delete from quotes");
        jdbc.update("delete from holdings");
        jdbc.update("delete from instruments");
        jdbc.update("delete from accounts");
    }

    @Given("an account {string} of type {word}")
    public void anAccountOfType(String name, String type) {
        CreateAccountRequest request = new CreateAccountRequest();
        request.setName(name);
        request.setType(AccountType.valueOf(type));
        request.setInstitution(name);
        accountId = restTemplate
                .postForEntity("/accounts", request, AccountResponse.class)
                .getBody()
                .getId();
    }

    @Given("an instrument {string} quoted by {word} as {string}")
    public void anInstrumentQuotedByAs(String name, String priceSource, String sourceRef) {
        instrumentId = insertInstrument(name, "EUR", "ETF", priceSource, sourceRef);
    }

    @Given("a USD instrument {string} quoted by {word} as {string}")
    public void aUsdInstrumentQuotedByAs(String name, String priceSource, String sourceRef) {
        instrumentId = insertInstrument(name, "USD", "EQUITY", priceSource, sourceRef);
    }

    @Given("a holding of {int} units bought at {bigdecimal}")
    public void aHoldingOfUnitsBoughtAt(int quantity, BigDecimal averageCost) {
        createHolding(quantity, averageCost);
    }

    @Given("a holding of {int} units with no cost basis")
    public void aHoldingOfUnitsWithNoCostBasis(int quantity) {
        createHolding(quantity, null);
    }

    @Given("a quote of {bigdecimal} EUR dated {word}")
    public void aQuoteOfEurDated(BigDecimal price, String asOf) {
        RecordQuoteRequest request = new RecordQuoteRequest();
        request.setAsOf(LocalDate.parse(asOf));
        request.setPrice(price);
        restTemplate.postForEntity("/instruments/{id}/quotes", request, Void.class, instrumentId);
    }

    @Given("a quote of {bigdecimal} USD dated {word}")
    public void aQuoteOfUsdDated(BigDecimal price, String asOf) {
        aQuoteOfEurDated(price, asOf);
    }

    @When("I read the portfolio")
    public void iReadThePortfolio() {
        portfolio =
                restTemplate.getForEntity("/portfolio", PortfolioResponse.class).getBody();
    }

    @Then("the total is {bigdecimal} EUR")
    public void theTotalIsEur(BigDecimal total) {
        assertThat(portfolio.getTotalEur()).isEqualByComparingTo(total);
    }

    @Then("the unrealized gain is {bigdecimal} EUR")
    public void theUnrealizedGainIsEur(BigDecimal gain) {
        assertThat(portfolio.getUnrealizedGainEur()).isEqualByComparingTo(gain);
    }

    @Then("no unrealized gain is reported")
    public void noUnrealizedGainIsReported() {
        assertThat(portfolio.getUnrealizedGainEur()).isNull();
    }

    @Then("the portfolio lists {int} holding with no price")
    public void thePortfolioListsHoldingWithNoPrice(int count) {
        assertThat(portfolio.getHoldings()).hasSize(count);
        assertThat(portfolio.getHoldings().get(0).getPrice()).isNull();
    }

    @Then("the unvalued count is {int}")
    public void theUnvaluedCountIs(int count) {
        assertThat(portfolio.getUnvaluedCount()).isEqualTo(count);
    }

    @Then("the non-EUR count is {int}")
    public void theNonEurCountIs(int count) {
        assertThat(portfolio.getNonEurCount()).isEqualTo(count);
    }

    @Then("the portfolio lists the USD holding priced in USD without a value in EUR")
    public void thePortfolioListsTheUsdHolding() {
        assertThat(portfolio.getHoldings())
                .filteredOn(holding -> "USD".equals(holding.getPriceCurrency()))
                .singleElement()
                .satisfies(holding -> {
                    assertThat(holding.getPrice()).isEqualByComparingTo("80");
                    assertThat(holding.getMarketValueEur()).isNull();
                    assertThat(holding.getDayChangeEur()).isNull();
                    assertThat(holding.getDayChangeRatio()).isNull();
                    assertThat(holding.getUnrealizedGainEur()).isNull();
                });
    }

    @Then("the one day performance totals {bigdecimal} EUR")
    public void theOneDayPerformanceTotals(BigDecimal total) {
        PerformanceResponse performance = restTemplate
                .getForEntity("/portfolio/performance?range=1d", PerformanceResponse.class)
                .getBody();
        assertThat(performance.getTotal().getValueEur()).isEqualByComparingTo(total);
    }

    @Then("the asset class allocation excludes {int} unpriced and {int} non-EUR lines")
    public void theAssetClassAllocationExcludes(int unpriced, int nonEur) {
        assertThat(classAllocation.getUnvaluedCount()).isEqualTo(unpriced);
        assertThat(classAllocation.getNonEurCount()).isEqualTo(nonEur);
    }

    @Then("the account allocation excludes {int} unpriced and {int} non-EUR lines")
    public void theAccountAllocationExcludes(int unpriced, int nonEur) {
        assertThat(accountAllocation.getUnvaluedCount()).isEqualTo(unpriced);
        assertThat(accountAllocation.getNonEurCount()).isEqualTo(nonEur);
    }

    @When("I read the allocation by asset class")
    public void iReadTheAllocationByAssetClass() {
        classAllocation = restTemplate
                .getForEntity("/portfolio/allocation/classes", AssetClassAllocationResponse.class)
                .getBody();
    }

    @When("I read the allocation by account")
    public void iReadTheAllocationByAccount() {
        accountAllocation = restTemplate
                .getForEntity("/portfolio/allocation/accounts", AccountAllocationResponse.class)
                .getBody();
    }

    @When("I read the portfolio and both allocations")
    public void iReadThePortfolioAndBothAllocations() {
        iReadThePortfolio();
        iReadTheAllocationByAssetClass();
        iReadTheAllocationByAccount();
    }

    @Then("the allocation total is {bigdecimal} EUR")
    public void theAllocationTotalIsEur(BigDecimal total) {
        BigDecimal actual = classAllocation != null ? classAllocation.getTotalEur() : accountAllocation.getTotalEur();
        assertThat(actual).isEqualByComparingTo(total);
    }

    @Then("the asset class {word} is worth {bigdecimal} EUR with a share of {bigdecimal} over {int} lines")
    public void theAssetClassIsWorth(String assetClass, BigDecimal value, BigDecimal share, int lines) {
        assertThat(classAllocation.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getAssetClass().getValue()).isEqualTo(assetClass);
            assertThat(item.getValueEur()).isEqualByComparingTo(value);
            assertThat(item.getShare()).isEqualByComparingTo(share);
            assertThat(item.getLineCount()).isEqualTo(lines);
        });
    }

    @Then("the account {string} of type {word} is worth {bigdecimal} EUR with a share of {bigdecimal} over {int} lines")
    public void theAccountIsWorth(String name, String type, BigDecimal value, BigDecimal share, int lines) {
        assertThat(accountAllocation.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getAccount().getName()).isEqualTo(name);
            assertThat(item.getAccount().getType().getValue()).isEqualTo(type);
            assertThat(item.getAccount().getId()).isEqualTo(accountId);
            assertThat(item.getValueEur()).isEqualByComparingTo(value);
            assertThat(item.getShare()).isEqualByComparingTo(share);
            assertThat(item.getLineCount()).isEqualTo(lines);
        });
    }

    @Then("both allocations match the portfolio breakdowns")
    public void bothAllocationsMatchThePortfolioBreakdowns() {
        assertThat(classAllocation.getTotalEur()).isEqualByComparingTo(portfolio.getTotalEur());
        assertThat(accountAllocation.getTotalEur()).isEqualByComparingTo(portfolio.getTotalEur());
        assertThat(classAllocation.getItems())
                .extracting(
                        item -> item.getAssetClass().getValue(), item -> item.getValueEur(), item -> item.getShare())
                .containsExactlyElementsOf(portfolio.getByAssetClass().stream()
                        .map(slice -> tuple(slice.getLabel(), slice.getValueEur(), slice.getShare()))
                        .toList());
        assertThat(accountAllocation.getItems())
                .extracting(item -> item.getAccount().getName(), item -> item.getValueEur(), item -> item.getShare())
                .containsExactlyElementsOf(portfolio.getByAccount().stream()
                        .map(slice -> tuple(slice.getLabel(), slice.getValueEur(), slice.getShare()))
                        .toList());
    }

    private UUID insertInstrument(
            String name, String currency, String assetClass, String priceSource, String sourceRef) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into instruments (id, name, currency, asset_class, price_source, source_ref, created_at)
                values (?, ?, ?, ?, ?, ?, now())
                """, id, name, currency, assetClass, priceSource, sourceRef);
        return id;
    }

    private void createHolding(int quantity, BigDecimal averageCost) {
        CreateHoldingRequest request = new CreateHoldingRequest();
        request.setAccountId(accountId);
        request.setInstrumentId(instrumentId);
        request.setQuantity(BigDecimal.valueOf(quantity));
        request.setAverageCost(averageCost);
        restTemplate.postForEntity("/holdings", request, Void.class);
    }
}
