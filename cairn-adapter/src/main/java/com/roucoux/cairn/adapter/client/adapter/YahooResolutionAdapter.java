package com.roucoux.cairn.adapter.client.adapter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.InstrumentCandidate;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.port.out.ResolveInstrumentPort;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class YahooResolutionAdapter implements ResolveInstrumentPort {

    private static final Pattern ISIN = Pattern.compile("[A-Z]{2}[A-Z0-9]{9}[0-9]");

    private final RestClient client;

    public YahooResolutionAdapter(@Qualifier("yahooRestClient") RestClient client) {
        this.client = client;
    }

    @Override
    public boolean supports(PriceSource source) {
        return source == PriceSource.YAHOO;
    }

    @Override
    public List<InstrumentCandidate> resolve(String query) {
        try {
            SearchResponse response = client.get()
                    .uri("/v1/finance/search?q={query}&quotesCount=5&newsCount=0", query)
                    .retrieve()
                    .body(SearchResponse.class);
            if (response == null || response.quotes() == null) {
                return List.of();
            }
            String isin = isinOf(query);
            return response.quotes().stream()
                    .map(quote -> toCandidate(quote, isin))
                    .toList();
        } catch (RestClientException failure) {
            return List.of();
        }
    }

    private static String isinOf(String query) {
        String normalised = query.strip().toUpperCase(Locale.ROOT);
        return ISIN.matcher(normalised).matches() ? normalised : null;
    }

    private static InstrumentCandidate toCandidate(SearchQuote quote, String isin) {
        return new InstrumentCandidate(
                quote.longname(),
                PriceSource.YAHOO,
                quote.symbol(),
                assetClassOf(quote.quoteType()),
                quote.exchDisp(),
                isin,
                quote.symbol(),
                null);
    }

    static AssetClass assetClassOf(String quoteType) {
        return switch (quoteType) {
            case "ETF" -> AssetClass.ETF;
            case "MUTUALFUND" -> AssetClass.FUND;
            default -> AssetClass.EQUITY;
        };
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SearchResponse(List<SearchQuote> quotes) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SearchQuote(String symbol, String longname, String quoteType, String exchDisp) {}
}
