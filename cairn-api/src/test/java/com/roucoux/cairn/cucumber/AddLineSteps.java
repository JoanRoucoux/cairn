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
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcOperations;

public class AddLineSteps {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcOperations jdbc;

    private final List<UUID> accountIds = new ArrayList<>();
    private ResponseEntity<HoldingResponse> lastResponse;

    @Before
    public void resetPortfolio() {
        jdbc.update("delete from quotes");
        jdbc.update("delete from holdings");
        jdbc.update("delete from instruments");
        jdbc.update("delete from accounts");
        accountIds.clear();
        lastResponse = null;
    }

    @Given("a brokerage account")
    public void aBrokerageAccount() {
        createAccount("Sample Broker");
    }

    @Given("a second brokerage account")
    public void aSecondBrokerageAccount() {
        createAccount("Other Broker");
    }

    @When("I add {int} units of the manual bond {string} priced at {bigdecimal}")
    public void iAddAManualBond(int quantity, String name, BigDecimal price) {
        add(accountIds.get(0), quantity, manual(name, price));
    }

    @When("I add {int} units of the manual bond {string} without a price")
    public void iAddAManualBondWithoutAPrice(int quantity, String name) {
        add(accountIds.get(0), quantity, manual(name, null));
    }

    @When("I add {int} units of the Sirius product {word}")
    public void iAddASiriusProduct(int quantity, String isin) {
        NewInstrumentRequest instrument = new NewInstrumentRequest(AssetClass.FUND, PriceSource.SG_SIRIUS);
        instrument.setIsin(isin);
        add(accountIds.get(0), quantity, instrument);
    }

    @When("I add {int} units of the Sirius product {word} to the second account")
    public void iAddASiriusProductToTheSecondAccount(int quantity, String isin) {
        NewInstrumentRequest instrument = new NewInstrumentRequest(AssetClass.FUND, PriceSource.SG_SIRIUS);
        instrument.setIsin(isin);
        add(accountIds.get(1), quantity, instrument);
    }

    @When("I add {int} unit of the Yahoo listing {string} to the first account")
    public void iAddAYahooListingToTheFirstAccount(int quantity, String ref) {
        add(accountIds.get(0), quantity, yahoo(ref, null));
    }

    @When("I add {int} units of the Yahoo listing {string} to the second account")
    public void iAddAYahooListingToTheSecondAccount(int quantity, String ref) {
        add(accountIds.get(1), quantity, yahoo(ref, null));
    }

    @When("I add {int} unit of the Yahoo listing {string} with the ISIN {word}")
    public void iAddAYahooListingWithAnIsin(int quantity, String ref, String isin) {
        add(accountIds.get(0), quantity, yahoo(ref, isin));
    }

    @When("I add {int} unit of the Yahoo listing {string} priced at {bigdecimal}")
    public void iAddAPricedYahooListing(int quantity, String ref, BigDecimal price) {
        NewInstrumentRequest instrument = yahoo(ref, null);
        instrument.setPrice(price);
        add(accountIds.get(0), quantity, instrument);
    }

    @When("I add {int} unit of the Amundi fund {word}")
    public void iAddAnAmundiFund(int quantity, String isin) {
        NewInstrumentRequest instrument = new NewInstrumentRequest(AssetClass.FUND, PriceSource.AMUNDI);
        instrument.setName("Northwind World");
        instrument.setIsin(isin);
        add(accountIds.get(0), quantity, instrument);
    }

    @Then("the add answers {int}")
    public void theAddAnswers(int status) {
        assertThat(lastResponse.getStatusCode().value()).isEqualTo(status);
    }

    @Then("the line is valued at {bigdecimal} EUR")
    public void theLineIsValuedAt(BigDecimal value) {
        assertThat(lastResponse.getBody().getMarketValueEur()).isEqualByComparingTo(value);
    }

    @Then("the line has no source reference and is not stale")
    public void theLineHasNoSourceReferenceAndIsNotStale() {
        assertThat(lastResponse.getBody().getSourceRef()).isNull();
        assertThat(lastResponse.getBody().getStale()).isFalse();
    }

    @Then("the line is the fund {string} with no price yet")
    public void theLineIsTheFundWithNoPriceYet(String name) {
        HoldingResponse line = lastResponse.getBody();
        assertThat(line.getInstrumentName()).isEqualTo(name);
        assertThat(line.getIsin()).isEqualTo(name);
        assertThat(line.getAssetClass()).isEqualTo(AssetClass.FUND);
        assertThat(line.getPrice()).isNull();
        assertThat(line.getMarketValueEur()).isNull();
    }

    @Then("the line has the source reference {string}")
    public void theLineHasTheSourceReference(String sourceRef) {
        assertThat(lastResponse.getBody().getSourceRef()).isEqualTo(sourceRef);
    }

    @Then("the portfolio holds {int} titles and {int} lines")
    public void thePortfolioHolds(int titles, int lines) {
        assertThat(jdbc.queryForObject("select count(*) from instruments", Integer.class))
                .isEqualTo(titles);
        assertThat(jdbc.queryForObject("select count(*) from holdings", Integer.class))
                .isEqualTo(lines);
    }

    private void createAccount(String name) {
        CreateAccountRequest request = new CreateAccountRequest();
        request.setName(name);
        request.setType(AccountType.CTO);
        request.setInstitution(name);
        accountIds.add(restTemplate
                .postForEntity("/accounts", request, AccountResponse.class)
                .getBody()
                .getId());
    }

    private static NewInstrumentRequest manual(String name, BigDecimal price) {
        NewInstrumentRequest instrument = new NewInstrumentRequest(AssetClass.BOND, PriceSource.MANUAL);
        instrument.setName(name);
        instrument.setPrice(price);
        return instrument;
    }

    private static NewInstrumentRequest yahoo(String ref, String isin) {
        NewInstrumentRequest instrument = new NewInstrumentRequest(AssetClass.ETF, PriceSource.YAHOO);
        instrument.setName("Northwind Index");
        instrument.setSourceRef(ref);
        instrument.setIsin(isin);
        return instrument;
    }

    private void add(UUID accountId, int quantity, NewInstrumentRequest instrument) {
        CreateHoldingRequest request = new CreateHoldingRequest();
        request.setAccountId(accountId);
        request.setQuantity(BigDecimal.valueOf(quantity));
        request.setInstrument(instrument);
        lastResponse = restTemplate.postForEntity("/holdings", request, HoldingResponse.class);
    }
}
