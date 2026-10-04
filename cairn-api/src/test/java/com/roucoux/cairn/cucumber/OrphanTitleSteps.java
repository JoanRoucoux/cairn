package com.roucoux.cairn.cucumber;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.generated.model.AccountResponse;
import com.roucoux.cairn.generated.model.AccountType;
import com.roucoux.cairn.generated.model.AssetClass;
import com.roucoux.cairn.generated.model.CreateAccountRequest;
import com.roucoux.cairn.generated.model.CreateHoldingRequest;
import com.roucoux.cairn.generated.model.HoldingResponse;
import com.roucoux.cairn.generated.model.NewInstrumentRequest;
import com.roucoux.cairn.generated.model.PriceSource;
import com.roucoux.cairn.generated.model.RecordQuoteRequest;
import com.roucoux.cairn.generated.model.SellHoldingRequest;
import com.roucoux.cairn.generated.model.SetCashBalanceRequest;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcOperations;

public class OrphanTitleSteps {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcOperations jdbc;

    private final List<UUID> accountIds = new ArrayList<>();
    private final Map<String, HoldingResponse> lines = new HashMap<>();

    @Before
    public void resetPortfolio() {
        jdbc.update("delete from quotes");
        jdbc.update("delete from holdings");
        jdbc.update("delete from instruments");
        jdbc.update("delete from accounts");
        accountIds.clear();
        lines.clear();
    }

    @Given("Alex holds {int} units of the Yahoo title {string} in the first account")
    public void alexHoldsInTheFirstAccount(int quantity, String ref) {
        hold(0, quantity, ref);
    }

    @Given("Alex holds {int} units of the Yahoo title {string} in the second account")
    public void alexHoldsInTheSecondAccount(int quantity, String ref) {
        hold(1, quantity, ref);
    }

    @Given("the title {string} has a recorded quote of {bigdecimal}")
    public void theTitleHasARecordedQuote(String ref, BigDecimal price) {
        RecordQuoteRequest request = new RecordQuoteRequest();
        request.setAsOf(LocalDate.parse("2026-08-21"));
        request.setPrice(price);
        restTemplate.postForEntity("/instruments/{id}/quotes", request, Void.class, instrumentIdOf(ref));
    }

    @Given("Alex deletes the line of the title {string} in the first account")
    public void alexDeletesTheLine(String ref) {
        restTemplate.exchange(
                "/holdings/{id}",
                HttpMethod.DELETE,
                null,
                Void.class,
                lineOf(0, ref).getId());
    }

    @When("Alex sells {int} units of the title {string} in the first account")
    public void alexSellsInTheFirstAccount(int quantity, String ref) {
        sell(0, quantity, ref);
    }

    @When("Alex sells {int} units of the title {string} in the second account")
    public void alexSellsInTheSecondAccount(int quantity, String ref) {
        sell(1, quantity, ref);
    }

    @Given("Alex holds a cash balance of {int} in the first account")
    public void alexHoldsACashBalance(int amount) {
        setCash(amount);
    }

    @When("Alex sets the cash balance of the first account to {int}")
    public void alexSetsTheCashBalance(int amount) {
        setCash(amount);
    }

    @When("Alex deletes the first account")
    public void alexDeletesTheFirstAccount() {
        restTemplate.exchange("/accounts/{id}", HttpMethod.DELETE, null, Void.class, accountIds.get(0));
    }

    @Then("the title {string} is still tracked")
    public void theTitleIsStillTracked(String ref) {
        assertThat(titleCount(ref)).isEqualTo(1);
    }

    @Then("the title {string} is no longer tracked")
    public void theTitleIsNoLongerTracked(String ref) {
        assertThat(titleCount(ref)).isZero();
    }

    @Then("the title {string} has {int} recorded quotes")
    public void theTitleHasRecordedQuotes(String ref, int expected) {
        Integer count = jdbc.queryForObject(
                "select count(*) from quotes q join instruments i on i.id = q.instrument_id where i.source_ref = ?",
                Integer.class,
                ref);
        assertThat(count).isEqualTo(expected);
    }

    @Then("the euro cash title still exists")
    public void theEuroCashTitleStillExists() {
        Integer count = jdbc.queryForObject(
                "select count(*) from instruments where asset_class = 'CASH' and source_ref = 'EUR'", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    private void hold(int accountIndex, int quantity, String ref) {
        NewInstrumentRequest instrument = new NewInstrumentRequest(AssetClass.ETF, PriceSource.YAHOO);
        instrument.setName("Northwind Index");
        instrument.setSourceRef(ref);
        CreateHoldingRequest request = new CreateHoldingRequest();
        request.setAccountId(account(accountIndex));
        request.setQuantity(BigDecimal.valueOf(quantity));
        request.setInstrument(instrument);
        lines.put(
                accountIndex + ref,
                restTemplate
                        .postForEntity("/holdings", request, HoldingResponse.class)
                        .getBody());
    }

    private void sell(int accountIndex, int quantity, String ref) {
        restTemplate.postForEntity(
                "/holdings/{id}/sell",
                new SellHoldingRequest(BigDecimal.valueOf(quantity)),
                Void.class,
                lineOf(accountIndex, ref).getId());
    }

    private void setCash(int amount) {
        restTemplate.exchange(
                "/accounts/{id}/cash",
                HttpMethod.PUT,
                new HttpEntity<>(new SetCashBalanceRequest(BigDecimal.valueOf(amount))),
                Void.class,
                account(0));
    }

    private UUID account(int index) {
        while (accountIds.size() <= index) {
            CreateAccountRequest request = new CreateAccountRequest();
            request.setName("Alex account " + accountIds.size());
            request.setType(AccountType.CTO);
            request.setInstitution("Northwind Bank");
            accountIds.add(restTemplate
                    .postForEntity("/accounts", request, AccountResponse.class)
                    .getBody()
                    .getId());
        }
        return accountIds.get(index);
    }

    private HoldingResponse lineOf(int accountIndex, String ref) {
        return lines.get(accountIndex + ref);
    }

    private UUID instrumentIdOf(String ref) {
        return lines.values().stream()
                .filter(line -> ref.equals(line.getSourceRef()))
                .findFirst()
                .orElseThrow()
                .getInstrumentId();
    }

    private int titleCount(String ref) {
        return jdbc.queryForObject("select count(*) from instruments where source_ref = ?", Integer.class, ref);
    }
}
