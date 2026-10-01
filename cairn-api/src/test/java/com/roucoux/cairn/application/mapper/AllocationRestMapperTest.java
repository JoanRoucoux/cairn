package com.roucoux.cairn.application.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.domain.model.Account;
import com.roucoux.cairn.domain.model.AccountAllocation;
import com.roucoux.cairn.domain.model.AccountBreakdown;
import com.roucoux.cairn.domain.model.AccountType;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.AssetClassAllocation;
import com.roucoux.cairn.domain.model.AssetClassBreakdown;
import com.roucoux.cairn.domain.model.Money;
import com.roucoux.cairn.generated.model.AccountAllocationResponse;
import com.roucoux.cairn.generated.model.AssetClassAllocationResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AllocationRestMapperTest {

    private final AllocationRestMapper mapper = new AllocationRestMapper(new AccountRestMapper());

    @Test
    void mapsAClassBreakdownRoundingMoneyToCentsAndSharesToSixDecimals() {
        AssetClassBreakdown breakdown = new AssetClassBreakdown(
                Money.eur(new BigDecimal("1000.456")),
                List.of(new AssetClassAllocation(
                        AssetClass.ETF, Money.eur(new BigDecimal("800.4549")), new BigDecimal("0.80012345678"), 3)));

        AssetClassAllocationResponse response = mapper.toResponse(breakdown);

        assertThat(response.getTotalEur()).isEqualByComparingTo("1000.46");
        assertThat(response.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getAssetClass().getValue()).isEqualTo("ETF");
            assertThat(item.getValueEur()).isEqualByComparingTo("800.45");
            assertThat(item.getShare()).isEqualByComparingTo("0.800123");
            assertThat(item.getLineCount()).isEqualTo(3);
        });
    }

    @Test
    void mapsAnAccountBreakdownCarryingTheWholeAccount() {
        Account account = new Account(UUID.randomUUID(), "Saxo", AccountType.PEA, "Saxo Bank");
        AccountBreakdown breakdown = new AccountBreakdown(
                Money.eur(new BigDecimal("1000")),
                List.of(new AccountAllocation(account, Money.eur(new BigDecimal("600")), new BigDecimal("0.6"), 2)));

        AccountAllocationResponse response = mapper.toResponse(breakdown);

        assertThat(response.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getAccount().getId()).isEqualTo(account.id());
            assertThat(item.getAccount().getName()).isEqualTo("Saxo");
            assertThat(item.getAccount().getType().getValue()).isEqualTo("PEA");
            assertThat(item.getAccount().getInstitution()).isEqualTo("Saxo Bank");
            assertThat(item.getValueEur()).isEqualByComparingTo("600");
            assertThat(item.getShare()).isEqualByComparingTo("0.6");
            assertThat(item.getLineCount()).isEqualTo(2);
        });
    }

    @Test
    void mapsAnEmptyBreakdownToAnEmptyList() {
        AssetClassBreakdown breakdown = new AssetClassBreakdown(Money.zeroEur(), List.of());

        assertThat(mapper.toResponse(breakdown).getItems()).isEmpty();
    }
}
