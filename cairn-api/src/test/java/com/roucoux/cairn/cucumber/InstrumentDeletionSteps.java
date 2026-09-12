package com.roucoux.cairn.cucumber;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.generated.model.AccountResponse;
import com.roucoux.cairn.generated.model.CreateAccountRequest;
import com.roucoux.cairn.generated.model.CreateHoldingRequest;
import com.roucoux.cairn.generated.model.CreateInstrumentRequest;
import com.roucoux.cairn.generated.model.InstrumentResponse;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcOperations;

/**
 * Step definitions for {@code instrument-deletion.feature}. Every scenario calls the real HTTP
 * API of the running application, the only level that exercises the real Liquibase cascades.
 */
public class InstrumentDeletionSteps {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcOperations jdbc;

    private UUID instrumentId;
    private ResponseEntity<Void> deleteResponse;

    /** Each scenario starts from an empty portfolio, though they share one database. */
    @Before
    public void resetPortfolio() {
        jdbc.update("delete from quotes");
        jdbc.update("delete from holdings");
        jdbc.update("delete from instruments");
        jdbc.update("delete from accounts");
    }

    @Given("an instrument to delete {string} priced by {word} as {string}")
    public void anInstrumentToDeletePricedByAs(String name, String priceSource, String sourceRef) {
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

    @Given("a holding to delete of {int} units bought at {bigdecimal}")
    public void aHoldingToDeleteOfUnitsBoughtAt(int quantity, BigDecimal averageCost) {
        CreateAccountRequest accountRequest = new CreateAccountRequest();
        accountRequest.setName("Sample Broker");
        accountRequest.setType(CreateAccountRequest.TypeEnum.CTO);
        accountRequest.setInstitution("Sample Broker");
        UUID accountId = restTemplate
                .postForEntity("/accounts", accountRequest, AccountResponse.class)
                .getBody()
                .getId();

        CreateHoldingRequest holdingRequest = new CreateHoldingRequest();
        holdingRequest.setAccountId(accountId);
        holdingRequest.setInstrumentId(instrumentId);
        holdingRequest.setQuantity(BigDecimal.valueOf(quantity));
        holdingRequest.setAverageCost(averageCost);
        restTemplate.postForEntity("/holdings", holdingRequest, Void.class);
    }

    @Given("a quote to delete of {bigdecimal} EUR dated {word}")
    public void aQuoteToDeleteOfEurDated(BigDecimal price, String asOf) {
        RecordQuoteRequest request = new RecordQuoteRequest();
        request.setAsOf(LocalDate.parse(asOf));
        request.setPrice(price);
        restTemplate.postForEntity("/instruments/{id}/quotes", request, Void.class, instrumentId);
    }

    @Given("the instrument is already deleted")
    public void theInstrumentIsAlreadyDeleted() {
        iDeleteTheInstrument();
    }

    @When("I delete the instrument")
    public void iDeleteTheInstrument() {
        deleteResponse = restTemplate.exchange("/instruments/{id}", HttpMethod.DELETE, null, Void.class, instrumentId);
    }

    @Then("the instrument is gone")
    public void theInstrumentIsGone() {
        assertThat(deleteResponse.getStatusCode().value()).isEqualTo(204);
        Integer count =
                jdbc.queryForObject("select count(*) from instruments where id = ?", Integer.class, instrumentId);
        assertThat(count).isZero();
    }

    @Then("the holding is gone")
    public void theHoldingIsGone() {
        Integer count = jdbc.queryForObject(
                "select count(*) from holdings where instrument_id = ?", Integer.class, instrumentId);
        assertThat(count).isZero();
    }

    @Then("the quote is gone")
    public void theQuoteIsGone() {
        Integer count =
                jdbc.queryForObject("select count(*) from quotes where instrument_id = ?", Integer.class, instrumentId);
        assertThat(count).isZero();
    }

    @Then("the deletion answers not found")
    public void theDeletionAnswersNotFound() {
        assertThat(deleteResponse.getStatusCode().value()).isEqualTo(404);
    }
}
