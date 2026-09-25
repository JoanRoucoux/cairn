package com.roucoux.cairn.cucumber;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.generated.model.AccountResponse;
import com.roucoux.cairn.generated.model.AccountType;
import com.roucoux.cairn.generated.model.CreateAccountRequest;
import com.roucoux.cairn.generated.model.HoldingResponse;
import com.roucoux.cairn.generated.model.SetCashBalanceRequest;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcOperations;

public class CashBalanceSteps {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcOperations jdbc;

    private UUID accountId;
    private ResponseEntity<Void> lastResponse;

    @Before
    public void resetPortfolio() {
        jdbc.update("delete from quotes");
        jdbc.update("delete from holdings");
        jdbc.update("delete from instruments");
        jdbc.update("delete from accounts");
    }

    @Given("an account {string}")
    public void anAccount(String name) {
        CreateAccountRequest request = new CreateAccountRequest();
        request.setName(name);
        request.setType(AccountType.SAVINGS);
        request.setInstitution(name);
        accountId = restTemplate
                .postForEntity("/accounts", request, AccountResponse.class)
                .getBody()
                .getId();
    }

    @When("I set its cash balance to {int}")
    public void iSetItsCashBalanceTo(int amount) {
        SetCashBalanceRequest request = new SetCashBalanceRequest(BigDecimal.valueOf(amount));
        lastResponse = restTemplate.exchange(
                "/accounts/{id}/cash",
                HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(request),
                Void.class,
                accountId);
        assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Then("holdings list a cash line of {int} for {string}")
    public void holdingsListACashLineOfFor(int amount, String accountName) {
        HoldingResponse cashLine = findCashLine(accountName);
        assertThat(cashLine.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(amount));
        assertThat(cashLine.getInstrumentName()).isEqualTo("Euros");
    }

    @Then("holdings list no cash line for {string}")
    public void holdingsListNoCashLineFor(String accountName) {
        List<HoldingResponse> holdings = listHoldings();
        assertThat(holdings.stream().filter(h -> h.getAccountName().equals(accountName)))
                .isEmpty();
    }

    private HoldingResponse findCashLine(String accountName) {
        return listHoldings().stream()
                .filter(h -> h.getAccountName().equals(accountName))
                .findFirst()
                .orElseThrow();
    }

    private List<HoldingResponse> listHoldings() {
        return restTemplate
                .exchange(
                        "/holdings",
                        HttpMethod.GET,
                        null,
                        new org.springframework.core.ParameterizedTypeReference<List<HoldingResponse>>() {})
                .getBody();
    }
}
