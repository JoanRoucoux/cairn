package com.roucoux.cairn.cucumber;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.generated.model.AccountResponse;
import com.roucoux.cairn.generated.model.AccountType;
import com.roucoux.cairn.generated.model.AssetClass;
import com.roucoux.cairn.generated.model.BuyHoldingRequest;
import com.roucoux.cairn.generated.model.ChangeHoldingInstrumentRequest;
import com.roucoux.cairn.generated.model.CreateAccountRequest;
import com.roucoux.cairn.generated.model.CreateHoldingRequest;
import com.roucoux.cairn.generated.model.CreateInstrumentRequest;
import com.roucoux.cairn.generated.model.HoldingResponse;
import com.roucoux.cairn.generated.model.InstrumentResponse;
import com.roucoux.cairn.generated.model.PortfolioResponse;
import com.roucoux.cairn.generated.model.PriceSource;
import com.roucoux.cairn.generated.model.RecordQuoteRequest;
import com.roucoux.cairn.generated.model.SellHoldingRequest;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcOperations;

public class TradeSteps {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcOperations jdbc;

    private UUID holdingId;
    private UUID accountId;
    private UUID otherInstrumentId;
    private HttpStatus moveStatus;
    private HoldingResponse moved;
    private UUID holdingInstrumentId;
    private PortfolioResponse portfolio;
    private HttpStatus lastStatus;

    @Before
    public void resetPortfolio() {
        jdbc.update("delete from quotes");
        jdbc.update("delete from holdings");
        jdbc.update("delete from instruments");
        jdbc.update("delete from accounts");
    }

    @Given("a holding of {int} units at an average cost of {bigdecimal}")
    public void aHoldingOfUnitsAtAnAverageCostOf(int quantity, BigDecimal averageCost) {
        createHolding(quantity, averageCost, "EUR");
    }

    @Given("a holding of {int} units without an average cost")
    public void aHoldingOfUnitsWithoutAnAverageCost(int quantity) {
        createHolding(quantity, null, "EUR");
    }

    @When("I buy {int} units at {bigdecimal}")
    public void iBuyUnitsAt(int quantity, BigDecimal unitPrice) {
        BuyHoldingRequest request = new BuyHoldingRequest(BigDecimal.valueOf(quantity), unitPrice);
        ResponseEntity<String> response = restTemplate.exchange(
                "/holdings/{id}/buy", HttpMethod.POST, new HttpEntity<>(request), String.class, holdingId);
        lastStatus = (HttpStatus) response.getStatusCode();
    }

    @When("I sell {int} units")
    public void iSellUnits(int quantity) {
        SellHoldingRequest request = new SellHoldingRequest(BigDecimal.valueOf(quantity));
        ResponseEntity<String> response = restTemplate.exchange(
                "/holdings/{id}/sell", HttpMethod.POST, new HttpEntity<>(request), String.class, holdingId);
        lastStatus = (HttpStatus) response.getStatusCode();
    }

    @Then("the holding has {int} units at an average cost of {bigdecimal}")
    public void theHoldingHasUnitsAtAnAverageCostOf(int quantity, BigDecimal averageCost) {
        HoldingResponse holding = findHolding();
        assertThat(holding.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(quantity));
        assertThat(holding.getAverageCost()).isEqualByComparingTo(averageCost);
    }

    @Given("a USD holding of {int} units at an average cost of {bigdecimal} quoted at {bigdecimal}")
    public void aUsdHolding(int quantity, BigDecimal averageCost, BigDecimal price) {
        createHolding(quantity, averageCost, "USD");
        RecordQuoteRequest request = new RecordQuoteRequest();
        request.setAsOf(LocalDate.now());
        request.setPrice(price);
        restTemplate.postForEntity("/instruments/{id}/quotes", request, Void.class, holdingInstrumentId);
    }

    @When("I look at the portfolio")
    public void iReadThePortfolio() {
        portfolio =
                restTemplate.getForEntity("/portfolio", PortfolioResponse.class).getBody();
    }

    @Then("the portfolio total is {bigdecimal} EUR with a non-EUR count of {int}")
    public void thePortfolioTotal(BigDecimal total, int nonEurCount) {
        assertThat(portfolio.getTotalEur()).isEqualByComparingTo(total);
        assertThat(portfolio.getNonEurCount()).isEqualTo(nonEurCount);
    }

    @Given("another instrument")
    public void anotherInstrument() {
        otherInstrumentId = createInstrument("Other ETF", "EUR");
    }

    @Given("a quote of {bigdecimal} on the other instrument")
    public void aQuoteOnTheOtherInstrument(BigDecimal price) {
        RecordQuoteRequest request = new RecordQuoteRequest();
        request.setAsOf(LocalDate.now());
        request.setPrice(price);
        restTemplate.postForEntity("/instruments/{id}/quotes", request, Void.class, otherInstrumentId);
    }

    @Given("the account already holds the other instrument")
    public void theAccountAlreadyHoldsTheOtherInstrument() {
        CreateHoldingRequest request = new CreateHoldingRequest();
        request.setAccountId(accountId);
        request.setInstrumentId(otherInstrumentId);
        request.setQuantity(BigDecimal.ONE);
        restTemplate.postForEntity("/holdings", request, HoldingResponse.class);
    }

    @When("I move the holding to the other instrument")
    public void iMoveTheHoldingToTheOtherInstrument() {
        ChangeHoldingInstrumentRequest request = new ChangeHoldingInstrumentRequest(otherInstrumentId);
        ResponseEntity<HoldingResponse> response = restTemplate.exchange(
                "/holdings/{id}/instrument",
                HttpMethod.PUT,
                new HttpEntity<>(request),
                HoldingResponse.class,
                holdingId);
        moveStatus = (HttpStatus) response.getStatusCode();
        moved = response.getBody();
    }

    @Then("the move answers {int}")
    public void theMoveAnswers(int status) {
        assertThat(moveStatus.value()).isEqualTo(status);
    }

    @Then("the moved holding is valued at {bigdecimal}")
    public void theMovedHoldingIsValuedAt(BigDecimal marketValue) {
        assertThat(moved.getMarketValueEur()).isEqualByComparingTo(marketValue);
    }

    @Then("the holding is on the other instrument")
    public void theHoldingIsOnTheOtherInstrument() {
        assertThat(findHolding().getInstrumentId()).isEqualTo(otherInstrumentId);
    }

    @Then("the sale answers {int}")
    public void theSaleAnswers(int status) {
        assertThat(lastStatus.value()).isEqualTo(status);
    }

    @Then("the holding no longer exists")
    public void theHoldingNoLongerExists() {
        assertThat(listHoldings().stream().map(HoldingResponse::getId)).doesNotContain(holdingId);
    }

    private void createHolding(int quantity, BigDecimal averageCost, String currency) {
        CreateAccountRequest accountRequest = new CreateAccountRequest();
        accountRequest.setName("Sample Broker");
        accountRequest.setType(AccountType.CTO);
        accountRequest.setInstitution("Sample Broker");
        accountId = restTemplate
                .postForEntity("/accounts", accountRequest, AccountResponse.class)
                .getBody()
                .getId();

        UUID instrumentId = createInstrument("Sample ETF", currency);

        holdingInstrumentId = instrumentId;
        CreateHoldingRequest holdingRequest = new CreateHoldingRequest();
        holdingRequest.setAccountId(accountId);
        holdingRequest.setInstrumentId(instrumentId);
        holdingRequest.setQuantity(BigDecimal.valueOf(quantity));
        holdingRequest.setAverageCost(averageCost);
        holdingId = restTemplate
                .postForEntity("/holdings", holdingRequest, HoldingResponse.class)
                .getBody()
                .getId();
    }

    private UUID createInstrument(String name, String currency) {
        CreateInstrumentRequest instrumentRequest = new CreateInstrumentRequest();
        instrumentRequest.setName(name);
        instrumentRequest.setCurrency(currency);
        instrumentRequest.setAssetClass(AssetClass.ETF);
        instrumentRequest.setPriceSource(PriceSource.MANUAL);
        return restTemplate
                .postForEntity("/instruments", instrumentRequest, InstrumentResponse.class)
                .getBody()
                .getId();
    }

    private HoldingResponse findHolding() {
        return listHoldings().stream()
                .filter(holding -> holding.getId().equals(holdingId))
                .findFirst()
                .orElseThrow();
    }

    private List<HoldingResponse> listHoldings() {
        return restTemplate
                .exchange("/holdings", HttpMethod.GET, null, new ParameterizedTypeReference<List<HoldingResponse>>() {})
                .getBody();
    }
}
