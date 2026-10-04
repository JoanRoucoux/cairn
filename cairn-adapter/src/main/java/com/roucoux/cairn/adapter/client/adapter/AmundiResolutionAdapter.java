package com.roucoux.cairn.adapter.client.adapter;

import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.InstrumentCandidate;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.port.out.ResolveInstrumentPort;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Order(2)
public class AmundiResolutionAdapter implements ResolveInstrumentPort {

    private static final Pattern ISIN = Pattern.compile("[A-Z]{2}[A-Z0-9]{9}[0-9]");

    private final AmundiShares shares;

    public AmundiResolutionAdapter(@Qualifier("amundiRestClient") RestClient client) {
        this.shares = new AmundiShares(client);
    }

    @Override
    public boolean supports(PriceSource source) {
        return source == PriceSource.AMUNDI;
    }

    @Override
    public List<InstrumentCandidate> resolve(String query) {
        String isin = query.strip().toUpperCase(Locale.ROOT);
        if (!ISIN.matcher(isin).matches()) {
            return List.of();
        }
        return shares
                .find(isin, "label", "lastNav")
                .filter(share -> share.label() != null && !share.label().isBlank())
                .map(share -> toCandidate(isin, share))
                .stream()
                .toList();
    }

    private static InstrumentCandidate toCandidate(String isin, AmundiShares.Share share) {
        AmundiShares.Nav nav = share.lastNav();
        boolean priced = nav != null && nav.value() != null;
        return new InstrumentCandidate(
                share.label(),
                PriceSource.AMUNDI,
                isin,
                AssetClass.FUND,
                null,
                isin,
                null,
                priced ? nav.value() : null,
                priced && nav.currency() != null ? nav.currency().iso3Code() : null,
                priced ? nav.date() : null,
                null);
    }
}
