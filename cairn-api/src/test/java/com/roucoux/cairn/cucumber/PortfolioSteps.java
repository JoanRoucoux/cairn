package com.roucoux.cairn.cucumber;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.generated.model.AccountResponse;
import com.roucoux.cairn.generated.model.CreateAccountRequest;
import com.roucoux.cairn.generated.model.CreateHoldingRequest;
import com.roucoux.cairn.generated.model.CreateInstrumentRequest;
import com.roucoux.cairn.generated.model.InstrumentResponse;
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

/**
 * Step definitions for {@code portfolio.feature}. Every scenario calls the real HTTP API of the
 * running application; nothing here talks to a repository or service directly.
 */
public class PortfolioSteps {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcOperations jdbc;

    private UUID accountId;
    private UUID instrumentId;
    private PortfolioResponse portfolio;

    /** Each scenario starts from an empty portfolio, though they share one database. */
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
        request.setType(CreateAccountRequest.TypeEnum.valueOf(type));
        request.setInstitution(name);
        accountId = restTemplate
                .postForEntity("/accounts", request, AccountResponse.class)
                .getBody()
                .getId();
    }

    @Given("an instrument {string} quoted by {word} as {string}")
    public void anInstrumentQuotedByAs(String name, String priceSource, String sourceRef) {
        CreateInstrumentRequest request = new CreateInstrumentRequest();
        request.setName(name);
        request.setCurrency("EUR");
        request.setAssetClass(CreateInstrumentRequest.AssetClassEnum.ETF);
        request.setPriceSource(CreateInstrumentRequest.PriceSourceEnum.valueOf(priceSource));
        request.setSourceRef(sourceRef);
        instrumentId = restTemplate
                .postForEntity("/instruments", request, InstrumentResponse.class)
                .getBody()
                .getId();
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

    private void createHolding(int quantity, BigDecimal averageCost) {
        CreateHoldingRequest request = new CreateHoldingRequest();
        request.setAccountId(accountId);
        request.setInstrumentId(instrumentId);
        request.setQuantity(BigDecimal.valueOf(quantity));
        request.setAverageCost(averageCost);
        restTemplate.postForEntity("/holdings", request, Void.class);
    }
}
