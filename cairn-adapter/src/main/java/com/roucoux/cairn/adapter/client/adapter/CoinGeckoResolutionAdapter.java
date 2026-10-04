package com.roucoux.cairn.adapter.client.adapter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.roucoux.cairn.domain.exception.technical.MarketDataRateLimitedException;
import com.roucoux.cairn.domain.exception.technical.MarketDataUnavailableException;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.InstrumentCandidate;
import com.roucoux.cairn.domain.model.Money;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.port.out.ResolveInstrumentPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@Order(3)
public class CoinGeckoResolutionAdapter implements ResolveInstrumentPort {

    private static final int MAX_COINS = 8;
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    private final RestClient client;

    public CoinGeckoResolutionAdapter(@Qualifier("coinGeckoRestClient") RestClient client) {
        this.client = client;
    }

    @Override
    public boolean supports(PriceSource source) {
        return source == PriceSource.COINGECKO;
    }

    @Override
    public List<InstrumentCandidate> resolve(String query) {
        try {
            List<Coin> coins = search(query);
            if (coins.isEmpty()) {
                return List.of();
            }
            Map<String, Map<String, BigDecimal>> prices = prices(coins);
            return coins.stream()
                    .map(coin -> toCandidate(coin, prices.get(coin.id())))
                    .toList();
        } catch (HttpClientErrorException.TooManyRequests throttled) {
            throw new MarketDataRateLimitedException("CoinGecko search throttled");
        } catch (RestClientException failure) {
            throw new MarketDataUnavailableException("CoinGecko search failed: " + failure.getMessage());
        }
    }

    private List<Coin> search(String query) {
        SearchResponse response = client.get()
                .uri("/api/v3/search?query={query}", query)
                .retrieve()
                .body(SearchResponse.class);
        if (response == null || response.coins() == null) {
            return List.of();
        }
        return response.coins().stream().limit(MAX_COINS).toList();
    }

    private Map<String, Map<String, BigDecimal>> prices(List<Coin> coins) {
        String ids = String.join(",", coins.stream().map(Coin::id).toList());
        Map<String, Map<String, BigDecimal>> response = client.get()
                .uri("/api/v3/simple/price?ids={ids}&vs_currencies=eur&include_last_updated_at=true", ids)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        return response == null ? Map.of() : response;
    }

    private static InstrumentCandidate toCandidate(Coin coin, Map<String, BigDecimal> price) {
        BigDecimal eur = price == null ? null : price.get("eur");
        BigDecimal updatedAt = price == null ? null : price.get("last_updated_at");
        return new InstrumentCandidate(
                coin.name() == null ? coin.id() : coin.name(),
                PriceSource.COINGECKO,
                coin.id(),
                AssetClass.CRYPTO,
                null,
                null,
                coin.symbol() == null ? null : coin.symbol().toUpperCase(Locale.ROOT),
                eur,
                eur == null ? null : Money.EUR,
                eur == null || updatedAt == null ? null : dateOf(updatedAt.longValue()),
                null);
    }

    private static LocalDate dateOf(long epochSeconds) {
        return Instant.ofEpochSecond(epochSeconds).atZone(PARIS).toLocalDate();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SearchResponse(List<Coin> coins) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Coin(String id, String name, String symbol) {}
}
