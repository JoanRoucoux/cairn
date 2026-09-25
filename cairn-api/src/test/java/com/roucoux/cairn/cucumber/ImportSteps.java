package com.roucoux.cairn.cucumber;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.generated.model.ImportReportResponse;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

public class ImportSteps {

    @Autowired
    private TestRestTemplate restTemplate;

    private ResponseEntity<ImportReportResponse> response;

    @When("I import:")
    public void iImport(String csv) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv"));
        response = restTemplate.postForEntity(
                "/portfolio/import", new HttpEntity<>(csv, headers), ImportReportResponse.class);
    }

    @Then("the import reports {int} created and {int} updated holdings")
    public void theImportReports(int created, int updated) {
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getHoldingsCreated()).isEqualTo(created);
        assertThat(response.getBody().getHoldingsUpdated()).isEqualTo(updated);
    }

    @Then("the import is refused")
    public void theImportIsRefused() {
        assertThat(response.getStatusCode().value()).isEqualTo(422);
    }
}
