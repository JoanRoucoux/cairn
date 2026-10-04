package com.roucoux.cairn.adapter.client.adapter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.roucoux.cairn.domain.exception.technical.MarketDataUnavailableException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

class AmundiShares {

    private static final String PATH = "/product-services/fdr/share/v3/full/{isin}";

    private final RestClient client;

    AmundiShares(RestClient client) {
        this.client = client;
    }

    Optional<Share> find(String isin, String... fields) {
        try {
            Share[] shares = client.post()
                    .uri(PATH, isin)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new FieldsRequest(List.of(fields)))
                    .retrieve()
                    .body(Share[].class);
            return shares == null || shares.length == 0 ? Optional.empty() : Optional.of(shares[0]);
        } catch (RestClientException failure) {
            throw new MarketDataUnavailableException("Amundi call failed for " + isin + ": " + failure.getMessage());
        }
    }

    private record FieldsRequest(List<String> fields) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Share(String label, Nav lastNav, List<Nav> navHistory) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Nav(LocalDate date, BigDecimal value, Currency currency) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Currency(String iso3Code) {}
}
