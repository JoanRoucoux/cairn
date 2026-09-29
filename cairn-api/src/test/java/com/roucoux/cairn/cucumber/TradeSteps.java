package com.roucoux.cairn.cucumber;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.generated.model.AccountResponse;
import com.roucoux.cairn.generated.model.AccountType;
import com.roucoux.cairn.generated.model.AssetClass;
import com.roucoux.cairn.generated.model.BuyHoldingRequest;
import com.roucoux.cairn.generated.model.CreateAccountRequest;
import com.roucoux.cairn.generated.model.CreateHoldingRequest;
import com.roucoux.cairn.generated.model.CreateInstrumentRequest;
import com.roucoux.cairn.generated.model.HoldingResponse;
import com.roucoux.cairn.generated.model.InstrumentResponse;
import com.roucoux.cairn.generated.model.PriceSource;
import com.roucoux.cairn.generated.model.SellHoldingRequest;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.math.BigDecimal;
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
        createHolding(quantity, averageCost);
    }

    @Given("a holding of {int} units without an average cost")
    public void aHoldingOfUnitsWithoutAnAverageCost(int quantity) {
        createHolding(quantity, null);
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

    @Then("the sale answers {int}")
    public void theSaleAnswers(int status) {
        assertThat(lastStatus.value()).isEqualTo(status);
    }

    @Then("the holding no longer exists")
    public void theHoldingNoLongerExists() {
        assertThat(listHoldings().stream().map(HoldingResponse::getId)).doesNotContain(holdingId);
    }

    private void createHolding(int quantity, BigDecimal averageCost) {
        CreateAccountRequest accountRequest = new CreateAccountRequest();
        accountRequest.setName("Sample Broker");
        accountRequest.setType(AccountType.CTO);
        accountRequest.setInstitution("Sample Broker");
        UUID accountId = restTemplate
                .postForEntity("/accounts", accountRequest, AccountResponse.class)
                .getBody()
                .getId();

        CreateInstrumentRequest instrumentRequest = new CreateInstrumentRequest();
        instrumentRequest.setName("Sample ETF");
        instrumentRequest.setCurrency("EUR");
        instrumentRequest.setAssetClass(AssetClass.ETF);
        instrumentRequest.setPriceSource(PriceSource.MANUAL);
        UUID instrumentId = restTemplate
                .postForEntity("/instruments", instrumentRequest, InstrumentResponse.class)
                .getBody()
                .getId();

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
