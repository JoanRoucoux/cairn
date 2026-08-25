package com.roucoux.cairn.cucumber;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.ResponseEntity;

/** Step definitions for quote.feature. */
public class QuoteSteps {

    @Autowired
    private TestRestTemplate restTemplate;

    private ResponseEntity<String> response;

    @Given("the market data provider knows the price of {string} as {string}")
    public void theMarketDataProviderKnowsThePriceOf(String isin, String price) {
        CucumberSpringConfiguration.MARKET_DATA.stubFor(get(urlEqualTo("/quotes/" + isin))
                .willReturn(okJson("{\"isin\":\"" + isin + "\",\"price\":" + price + "}")));
    }

    @Given("the market data provider does not know {string}")
    public void theMarketDataProviderDoesNotKnow(String isin) {
        CucumberSpringConfiguration.MARKET_DATA.stubFor(
                get(urlEqualTo("/quotes/" + isin)).willReturn(aResponse().withStatus(404)));
    }

    @Given("the market data provider is failing for {string}")
    public void theMarketDataProviderIsFailingFor(String isin) {
        CucumberSpringConfiguration.MARKET_DATA.stubFor(
                get(urlEqualTo("/quotes/" + isin)).willReturn(aResponse().withStatus(503)));
    }

    @When("I ask for the quote of {string}")
    public void iAskForTheQuoteOf(String isin) {
        response = restTemplate.getForEntity("/quote/{isin}", String.class, isin);
    }

    @Then("the response status is {int}")
    public void theResponseStatusIs(int status) {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
    }

    @Then("the quoted price is {string}")
    public void theQuotedPriceIs(String price) {
        assertThat(response.getBody()).contains(price);
    }
}
