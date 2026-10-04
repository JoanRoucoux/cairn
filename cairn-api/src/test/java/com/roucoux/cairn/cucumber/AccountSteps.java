package com.roucoux.cairn.cucumber;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.generated.model.AccountResponse;
import com.roucoux.cairn.generated.model.AccountType;
import com.roucoux.cairn.generated.model.CreateAccountRequest;
import com.roucoux.cairn.generated.model.CreateHoldingRequest;
import com.roucoux.cairn.generated.model.SetCashBalanceRequest;
import com.roucoux.cairn.generated.model.UpdateAccountRequest;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcOperations;

public class AccountSteps {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcOperations jdbc;

    private final Map<String, UUID> accountIdByName = new HashMap<>();
    private UUID lastAccountId;
    private ResponseEntity<AccountResponse> updateResponse;
    private ResponseEntity<Void> deleteResponse;

    @Before
    public void resetPortfolio() {
        jdbc.update("delete from quotes");
        jdbc.update("delete from holdings");
        jdbc.update("delete from instruments");
        jdbc.update("delete from accounts");
    }

    @Given("an account named {string} of type {word}")
    public void anAccountNamedOfType(String name, String type) {
        CreateAccountRequest request = new CreateAccountRequest();
        request.setName(name);
        request.setType(AccountType.valueOf(type));
        request.setInstitution(name);
        UUID id = restTemplate
                .postForEntity("/accounts", request, AccountResponse.class)
                .getBody()
                .getId();
        accountIdByName.put(name, id);
        lastAccountId = id;
    }

    @Given("its cash balance is {int}")
    public void itsCashBalanceIs(int amount) {
        SetCashBalanceRequest request = new SetCashBalanceRequest(BigDecimal.valueOf(amount));
        restTemplate.exchange(
                "/accounts/{id}/cash", HttpMethod.PUT, new HttpEntity<>(request), Void.class, lastAccountId);
    }

    @Given("it holds {int} units of a manual cash instrument {string}")
    public void itHoldsUnitsOfAManualCashInstrument(int quantity, String instrumentName) {
        UUID instrumentId = UUID.randomUUID();
        jdbc.update("""
                insert into instruments (id, name, currency, asset_class, price_source, created_at)
                values (?, ?, 'EUR', 'CASH', 'MANUAL', now())
                """, instrumentId, instrumentName);

        CreateHoldingRequest holdingRequest = new CreateHoldingRequest();
        holdingRequest.setAccountId(lastAccountId);
        holdingRequest.setInstrumentId(instrumentId);
        holdingRequest.setQuantity(BigDecimal.valueOf(quantity));
        restTemplate.postForEntity("/holdings", holdingRequest, Void.class);
    }

    @When("I update it to {string} of type {word} at {string}")
    public void iUpdateItToOfTypeAt(String name, String type, String institution) {
        updateResponse = update(lastAccountId, name, type, institution);
    }

    @When("I update {string} to {string} of type {word} at {string}")
    public void iUpdateToOfTypeAt(String currentName, String newName, String type, String institution) {
        updateResponse = update(accountIdByName.get(currentName), newName, type, institution);
    }

    @When("I delete it")
    public void iDeleteIt() {
        deleteResponse = restTemplate.exchange("/accounts/{id}", HttpMethod.DELETE, null, Void.class, lastAccountId);
    }

    @Then("the accounts list {string} of type {word} at {string}")
    public void theAccountsListOfTypeAt(String name, String type, String institution) {
        AccountResponse account = findAccountByName(name);
        assertThat(account.getType()).isEqualTo(AccountType.valueOf(type));
        assertThat(account.getInstitution()).isEqualTo(institution);
    }

    @Then("the update answers {int}")
    public void theUpdateAnswers(int status) {
        assertThat(updateResponse.getStatusCode().value()).isEqualTo(status);
    }

    @Then("the deletion answers {int}")
    public void theDeletionAnswers(int status) {
        assertThat(deleteResponse.getStatusCode().value()).isEqualTo(status);
    }

    @Then("no holding is left for that account")
    public void noHoldingIsLeftForThatAccount() {
        Integer count =
                jdbc.queryForObject("select count(*) from holdings where account_id = ?", Integer.class, lastAccountId);
        assertThat(count).isZero();
    }

    @Then("the accounts still list {string}")
    public void theAccountsStillList(String name) {
        assertThat(findAccountByName(name)).isNotNull();
    }

    private ResponseEntity<AccountResponse> update(UUID id, String name, String type, String institution) {
        UpdateAccountRequest request = new UpdateAccountRequest(name, AccountType.valueOf(type), institution);
        return restTemplate.exchange(
                "/accounts/{id}", HttpMethod.PUT, new HttpEntity<>(request), AccountResponse.class, id);
    }

    private List<AccountResponse> listAccounts() {
        return restTemplate
                .exchange(
                        "/accounts",
                        HttpMethod.GET,
                        null,
                        new org.springframework.core.ParameterizedTypeReference<List<AccountResponse>>() {})
                .getBody();
    }

    private AccountResponse findAccountByName(String name) {
        return listAccounts().stream()
                .filter(account -> account.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
