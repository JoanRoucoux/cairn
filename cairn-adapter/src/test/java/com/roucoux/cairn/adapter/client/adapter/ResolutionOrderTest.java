package com.roucoux.cairn.adapter.client.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.domain.port.out.ResolveInstrumentPort;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.web.client.RestClient;

class ResolutionOrderTest {

    @Test
    void yahooListingsComeBeforeAmundiNavsSoTheImportKeepsPickingTheListing() {
        RestClient client = RestClient.create();
        List<ResolveInstrumentPort> resolvers = new ArrayList<>(List.of(
                new CoinGeckoResolutionAdapter(client),
                new AmundiResolutionAdapter(client),
                new YahooResolutionAdapter(client)));

        AnnotationAwareOrderComparator.sort(resolvers);

        assertThat(resolvers)
                .extracting(resolver -> resolver.getClass().getSimpleName())
                .containsExactly("YahooResolutionAdapter", "AmundiResolutionAdapter", "CoinGeckoResolutionAdapter");
    }
}
