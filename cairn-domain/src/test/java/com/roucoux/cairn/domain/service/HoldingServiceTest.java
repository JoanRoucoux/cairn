package com.roucoux.cairn.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.roucoux.cairn.domain.exception.business.CashHoldingTradeException;
import com.roucoux.cairn.domain.exception.business.DuplicateHoldingException;
import com.roucoux.cairn.domain.exception.business.InvalidInstrumentException;
import com.roucoux.cairn.domain.exception.business.NotFoundException;
import com.roucoux.cairn.domain.exception.business.SavingsAccountLineException;
import com.roucoux.cairn.domain.exception.business.ZeroQuantityException;
import com.roucoux.cairn.domain.model.Account;
import com.roucoux.cairn.domain.model.AccountType;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Holding;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.NewInstrument;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.port.out.DeleteHoldingPort;
import com.roucoux.cairn.domain.port.out.LoadAccountsPort;
import com.roucoux.cairn.domain.port.out.LoadHoldingsPort;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import com.roucoux.cairn.domain.port.out.SaveHoldingPort;
import com.roucoux.cairn.domain.port.out.SaveQuotePort;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HoldingServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-12T08:30:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void createsAHolding() {
        Fixture fixture = Fixture.withKnownAccountAndInstrument();

        Holding created = fixture.service()
                .create(fixture.accountId(), fixture.instrumentId(), new BigDecimal("4"), new BigDecimal("43.64"));

        assertThat(created.quantity()).isEqualByComparingTo("4");
        assertThat(created.averageCost()).isEqualByComparingTo("43.64");
        assertThat(created.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void acceptsAHoldingWithoutACostBasis() {
        Fixture fixture = Fixture.withKnownAccountAndInstrument();

        Holding created =
                fixture.service().create(fixture.accountId(), fixture.instrumentId(), new BigDecimal("296"), null);

        assertThat(created.averageCost()).isNull();
    }

    @Test
    void aSavingsAccountRefusesALineThatIsNotTheEuroCashBalance() {
        Instrument etf = new Instrument(
                UUID.randomUUID(), "ETF", "FR0011871128", "EUR", AssetClass.ETF, PriceSource.YAHOO, "E.PA", null);
        Fixture fixture = Fixture.withSavingsAccountAndInstrument(etf);

        assertThatThrownBy(() -> fixture.service().create(fixture.accountId(), etf.id(), BigDecimal.ONE, null))
                .isInstanceOf(SavingsAccountLineException.class)
                .hasMessage("A savings account holds one balance, not lines");
        assertThat(fixture.holdings()).isEmpty();
    }

    @Test
    void aSavingsAccountRefusesABookletInstrumentToo() {
        Instrument booklet = new Instrument(
                UUID.randomUUID(), "Livret A", null, "EUR", AssetClass.CASH, PriceSource.MANUAL, null, null);
        Fixture fixture = Fixture.withSavingsAccountAndInstrument(booklet);

        assertThatThrownBy(() -> fixture.service().create(fixture.accountId(), booklet.id(), BigDecimal.ONE, null))
                .isInstanceOf(SavingsAccountLineException.class);
    }

    @Test
    void aSavingsAccountAcceptsItsEuroCashBalance() {
        Instrument euros = new Instrument(
                UUID.randomUUID(), "Euros", null, "EUR", AssetClass.CASH, PriceSource.MANUAL, "EUR", null);
        Fixture fixture = Fixture.withSavingsAccountAndInstrument(euros);

        Holding created = fixture.service().create(fixture.accountId(), euros.id(), new BigDecimal("1500"), null);

        assertThat(created.quantity()).isEqualByComparingTo("1500");
    }

    @Test
    void rejectsASecondHoldingOfTheSameInstrumentInTheSameAccount() {
        Fixture fixture = Fixture.withExistingHolding();

        assertThatThrownBy(() ->
                        fixture.service().create(fixture.accountId(), fixture.instrumentId(), BigDecimal.ONE, null))
                .isInstanceOf(DuplicateHoldingException.class);
    }

    @Test
    void rejectsAZeroQuantity() {
        Fixture fixture = Fixture.withKnownAccountAndInstrument();

        assertThatThrownBy(() ->
                        fixture.service().create(fixture.accountId(), fixture.instrumentId(), BigDecimal.ZERO, null))
                .isInstanceOf(ZeroQuantityException.class)
                .hasMessageContaining("quantity");
    }

    @Test
    void rejectsAnUnknownAccount() {
        Fixture fixture = Fixture.withKnownAccountAndInstrument();

        assertThatThrownBy(
                        () -> fixture.service().create(UUID.randomUUID(), fixture.instrumentId(), BigDecimal.ONE, null))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void rejectsAnUnknownInstrument() {
        Fixture fixture = Fixture.withKnownAccountAndInstrument();

        assertThatThrownBy(() -> fixture.service().create(fixture.accountId(), UUID.randomUUID(), BigDecimal.ONE, null))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updatesQuantityAndCostBasis() {
        Fixture fixture = Fixture.withExistingHolding();

        Holding updated = fixture.service().update(fixture.holdingId(), new BigDecimal("31"), new BigDecimal("394.25"));

        assertThat(updated.quantity()).isEqualByComparingTo("31");
        assertThat(updated.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsUpdatingAnUnknownHolding() {
        Fixture fixture = Fixture.withExistingHolding();

        assertThatThrownBy(() -> fixture.service().update(UUID.randomUUID(), BigDecimal.ONE, null))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void rejectsUpdatingWithAZeroQuantity() {
        Fixture fixture = Fixture.withExistingHolding();

        assertThatThrownBy(() -> fixture.service().update(fixture.holdingId(), BigDecimal.ZERO, null))
                .isInstanceOf(ZeroQuantityException.class)
                .hasMessageContaining("quantity");
    }

    @Test
    void deletesAHolding() {
        Fixture fixture = Fixture.withExistingHolding();

        fixture.service().delete(fixture.holdingId());

        assertThat(fixture.deleted()).containsExactly(fixture.holdingId());
    }

    @Test
    void rejectsDeletingAnUnknownHolding() {
        Fixture fixture = Fixture.withExistingHolding();

        assertThatThrownBy(() -> fixture.service().delete(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void buyingSavesTheWeightedHolding() {
        Fixture fixture = Fixture.withHolding("500", "24.12");

        Holding bought = fixture.service().buy(fixture.holdingId(), new BigDecimal("40"), new BigDecimal("29.10"));

        assertThat(bought.averageCost()).isEqualByComparingTo("24.488889");
        assertThat(bought.updatedAt()).isEqualTo(NOW);
        assertThat(fixture.holdings())
                .singleElement()
                .satisfies(saved -> assertThat(saved.quantity()).isEqualByComparingTo("540"));
    }

    @Test
    void sellingPartSavesTheRemainder() {
        Fixture fixture = Fixture.withHolding("500", "24.12");

        Optional<Holding> remaining = fixture.service().sell(fixture.holdingId(), new BigDecimal("100"));

        assertThat(remaining).isPresent();
        assertThat(fixture.holdings())
                .singleElement()
                .satisfies(saved -> assertThat(saved.quantity()).isEqualByComparingTo("400"));
    }

    @Test
    void sellingEverythingDeletesTheHolding() {
        Fixture fixture = Fixture.withHolding("500", "24.12");

        Optional<Holding> remaining = fixture.service().sell(fixture.holdingId(), new BigDecimal("500"));

        assertThat(remaining).isEmpty();
        assertThat(fixture.holdings()).isEmpty();
    }

    @Test
    void tradingAnUnknownHoldingIsNotFound() {
        Fixture fixture = Fixture.withKnownAccountAndInstrument();

        assertThatThrownBy(() -> fixture.service().buy(UUID.randomUUID(), BigDecimal.ONE, BigDecimal.ONE))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> fixture.service().sell(UUID.randomUUID(), BigDecimal.ONE))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void cashCannotBeTraded() {
        Fixture fixture = Fixture.withExistingCashHolding();

        assertThatThrownBy(() -> fixture.service().buy(fixture.holdingId(), BigDecimal.ONE, BigDecimal.ONE))
                .isInstanceOf(CashHoldingTradeException.class);
        assertThatThrownBy(() -> fixture.service().sell(fixture.holdingId(), BigDecimal.ONE))
                .isInstanceOf(CashHoldingTradeException.class);
    }

    @Test
    void movesAHoldingToAnotherInstrumentKeepingQuantityCostAndId() {
        Fixture fixture = Fixture.withHolding("12", "101.5");
        Instrument xetra = fixture.addInstrument("EUR", AssetClass.ETF);

        Holding moved = fixture.service().changeInstrument(fixture.holdingId(), xetra.id());

        assertThat(moved.id()).isEqualTo(fixture.holdingId());
        assertThat(moved.instrumentId()).isEqualTo(xetra.id());
        assertThat(moved.quantity()).isEqualByComparingTo("12");
        assertThat(moved.averageCost()).isEqualByComparingTo("101.5");
        assertThat(moved.updatedAt()).isEqualTo(NOW);
        assertThat(fixture.holdings()).containsExactly(moved);
    }

    @Test
    void movingToTheSameInstrumentChangesNothing() {
        Fixture fixture = Fixture.withHolding("12", "101.5");

        Holding same = fixture.service().changeInstrument(fixture.holdingId(), fixture.instrumentId());

        assertThat(same.updatedAt()).isEqualTo(Instant.EPOCH);
        assertThat(fixture.holdings()).containsExactly(same);
    }

    @Test
    void movingToAnInstrumentTheAccountAlreadyHoldsConflicts() {
        Fixture fixture = Fixture.withHolding("12", "101.5");
        Instrument xetra = fixture.addInstrument("EUR", AssetClass.ETF);
        fixture.holdings()
                .add(new Holding(UUID.randomUUID(), fixture.accountId(), xetra.id(), BigDecimal.ONE, null, NOW));

        assertThatThrownBy(() -> fixture.service().changeInstrument(fixture.holdingId(), xetra.id()))
                .isInstanceOf(DuplicateHoldingException.class);
    }

    @Test
    void movingToAnUnknownInstrumentOrHoldingIsNotFound() {
        Fixture fixture = Fixture.withHolding("12", "101.5");

        assertThatThrownBy(() -> fixture.service().changeInstrument(fixture.holdingId(), UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> fixture.service().changeInstrument(UUID.randomUUID(), fixture.instrumentId()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void aSavingsLineCannotMoveToANonCashInstrument() {
        Instrument euros = new Instrument(
                UUID.randomUUID(), "Euros", null, "EUR", AssetClass.CASH, PriceSource.MANUAL, "EUR", null);
        Fixture fixture = Fixture.withSavingsAccountAndInstrument(euros);
        fixture.holdings()
                .add(new Holding(fixture.holdingId(), fixture.accountId(), euros.id(), BigDecimal.TEN, null, NOW));
        Instrument etf = fixture.addInstrument("EUR", AssetClass.ETF);

        assertThatThrownBy(() -> fixture.service().changeInstrument(fixture.holdingId(), etf.id()))
                .isInstanceOf(SavingsAccountLineException.class);
    }

    private static NewInstrument manual(String price) {
        return new NewInstrument(
                "Woodgrove Notes",
                AssetClass.BOND,
                PriceSource.MANUAL,
                null,
                null,
                null,
                price == null ? null : new BigDecimal(price));
    }

    private static NewInstrument listed(PriceSource source, String sourceRef, String isin) {
        return new NewInstrument("Northwind Index", AssetClass.ETF, source, sourceRef, isin, "NWI", null);
    }

    @Test
    void aNewManualInstrumentIsCreatedWithTodaysQuoteAndItsHolding() {
        Fixture fixture = Fixture.withKnownAccountAndInstrument();

        Holding created = fixture.service()
                .createWithNewInstrument(fixture.accountId(), manual("42.10"), new BigDecimal("3"), null);

        Instrument instrument = fixture.instruments().stream()
                .filter(i -> i.id().equals(created.instrumentId()))
                .findFirst()
                .orElseThrow();
        assertThat(instrument.priceSource()).isEqualTo(PriceSource.MANUAL);
        assertThat(instrument.currency()).isEqualTo("EUR");
        assertThat(instrument.assetClass()).isEqualTo(AssetClass.BOND);
        assertThat(fixture.quotes()).singleElement().satisfies(quote -> {
            assertThat(quote.instrumentId()).isEqualTo(instrument.id());
            assertThat(quote.price()).isEqualByComparingTo("42.10");
            assertThat(quote.asOf()).isEqualTo(LocalDate.of(2026, 9, 12));
            assertThat(quote.source()).isEqualTo(PriceSource.MANUAL);
            assertThat(quote.currency()).isEqualTo("EUR");
        });
        assertThat(fixture.holdings()).containsExactly(created);
    }

    @Test
    void aNewManualInstrumentNeedsAPositivePrice() {
        Fixture fixture = Fixture.withKnownAccountAndInstrument();

        for (String price : new String[] {null, "0", "-1"}) {
            assertThatThrownBy(() -> fixture.service()
                            .createWithNewInstrument(fixture.accountId(), manual(price), BigDecimal.ONE, null))
                    .isInstanceOf(InvalidInstrumentException.class);
        }
        assertThat(fixture.instruments()).hasSize(1);
        assertThat(fixture.quotes()).isEmpty();
        assertThat(fixture.holdings()).isEmpty();
    }

    @Test
    void aPriceIsRefusedOnAnotherSource() {
        Fixture fixture = Fixture.withKnownAccountAndInstrument();
        NewInstrument priced = new NewInstrument(
                "Northwind Index", AssetClass.ETF, PriceSource.YAHOO, "NWI.PA", null, null, BigDecimal.TEN);

        assertThatThrownBy(() ->
                        fixture.service().createWithNewInstrument(fixture.accountId(), priced, BigDecimal.ONE, null))
                .isInstanceOf(InvalidInstrumentException.class);
        assertThat(fixture.instruments()).hasSize(1);
    }

    @Test
    void aSiriusInstrumentTakesItsIsinAsNameWhateverTheRequestSaysAndIsAFund() {
        Fixture fixture = Fixture.withKnownAccountAndInstrument();
        NewInstrument sirius =
                new NewInstrument("Whatever", AssetClass.FUND, PriceSource.SG_SIRIUS, null, "qs0009876543", null, null);

        Holding created = fixture.service().createWithNewInstrument(fixture.accountId(), sirius, BigDecimal.ONE, null);

        Instrument instrument = fixture.instruments().stream()
                .filter(i -> i.id().equals(created.instrumentId()))
                .findFirst()
                .orElseThrow();
        assertThat(instrument.name()).isEqualTo("QS0009876543");
        assertThat(instrument.isin()).isEqualTo("QS0009876543");
        assertThat(instrument.sourceRef()).isEqualTo("QS0009876543");
        assertThat(instrument.assetClass()).isEqualTo(AssetClass.FUND);
        assertThat(fixture.quotes()).isEmpty();
    }

    @Test
    void aSiriusOrAmundiInstrumentNeedsAValidIsin() {
        Fixture fixture = Fixture.withKnownAccountAndInstrument();

        for (PriceSource source : new PriceSource[] {PriceSource.SG_SIRIUS, PriceSource.AMUNDI}) {
            for (String isin : new String[] {null, "TOO-SHORT", "QS000987654X"}) {
                NewInstrument invalid = new NewInstrument("Fund", AssetClass.FUND, source, isin, isin, null, null);
                assertThatThrownBy(() -> fixture.service()
                                .createWithNewInstrument(fixture.accountId(), invalid, BigDecimal.ONE, null))
                        .isInstanceOf(InvalidInstrumentException.class);
            }
        }
        assertThat(fixture.instruments()).hasSize(1);
    }

    @Test
    void aCashInstrumentCannotBeCreatedInline() {
        Fixture fixture = Fixture.withKnownAccountAndInstrument();
        NewInstrument cash =
                new NewInstrument("Euros", AssetClass.CASH, PriceSource.MANUAL, null, null, null, BigDecimal.ONE);

        assertThatThrownBy(() ->
                        fixture.service().createWithNewInstrument(fixture.accountId(), cash, BigDecimal.ONE, null))
                .isInstanceOf(InvalidInstrumentException.class);
    }

    @Test
    void bondAndOtherAreRefusedOnAnythingButAManualInstrument() {
        Fixture fixture = Fixture.withKnownAccountAndInstrument();

        for (AssetClass assetClass : new AssetClass[] {AssetClass.BOND, AssetClass.OTHER}) {
            NewInstrument listed =
                    new NewInstrument("Northwind Index", assetClass, PriceSource.YAHOO, "NWD.PA", null, null, null);
            assertThatThrownBy(() -> fixture.service()
                            .createWithNewInstrument(fixture.accountId(), listed, BigDecimal.ONE, null))
                    .isInstanceOf(InvalidInstrumentException.class);
        }
        assertThat(fixture.instruments()).hasSize(1);
    }

    @Test
    void aListedInstrumentNeedsASourceReferenceAndAName() {
        Fixture fixture = Fixture.withKnownAccountAndInstrument();
        NewInstrument noReference = listed(PriceSource.YAHOO, " ", null);
        NewInstrument noName = new NewInstrument(" ", AssetClass.ETF, PriceSource.YAHOO, "NWI.PA", null, null, null);

        assertThatThrownBy(() -> fixture.service()
                        .createWithNewInstrument(fixture.accountId(), noReference, BigDecimal.ONE, null))
                .isInstanceOf(InvalidInstrumentException.class);
        assertThatThrownBy(() ->
                        fixture.service().createWithNewInstrument(fixture.accountId(), noName, BigDecimal.ONE, null))
                .isInstanceOf(InvalidInstrumentException.class);
    }

    @Test
    void aTrackedSourceAndReferenceIsReusedRatherThanDuplicated() {
        Fixture fixture = Fixture.withKnownAccountAndInstrument();
        Instrument tracked = fixture.addListed(PriceSource.YAHOO, "NWI.PA", "LU0000000002");

        Holding created = fixture.service()
                .createWithNewInstrument(
                        fixture.accountId(), listed(PriceSource.YAHOO, "NWI.PA", null), BigDecimal.ONE, null);

        assertThat(created.instrumentId()).isEqualTo(tracked.id());
        assertThat(fixture.instruments()).hasSize(2);
    }

    @Test
    void aTrackedTitleAlreadyHeldInTheAccountIsADuplicate() {
        Fixture fixture = Fixture.withKnownAccountAndInstrument();
        Instrument tracked = fixture.addListed(PriceSource.YAHOO, "NWI.PA", null);
        fixture.holdings()
                .add(new Holding(
                        UUID.randomUUID(), fixture.accountId(), tracked.id(), BigDecimal.ONE, null, Instant.EPOCH));

        assertThatThrownBy(() -> fixture.service()
                        .createWithNewInstrument(
                                fixture.accountId(), listed(PriceSource.YAHOO, "NWI.PA", null), BigDecimal.ONE, null))
                .isInstanceOf(DuplicateHoldingException.class);
        assertThat(fixture.instruments()).hasSize(2);
    }

    @Test
    void theSameIsinAtTwoSourcesGivesTwoInstruments() {
        Fixture fixture = Fixture.withKnownAccountAndInstrument();
        fixture.addListed(PriceSource.YAHOO, "NWD.PA", "LU0000000001");

        fixture.service()
                .createWithNewInstrument(
                        fixture.accountId(),
                        new NewInstrument(
                                "Northwind World",
                                AssetClass.FUND,
                                PriceSource.AMUNDI,
                                null,
                                "LU0000000001",
                                null,
                                null),
                        BigDecimal.ONE,
                        null);

        assertThat(fixture.instruments())
                .filteredOn(i -> "LU0000000001".equals(i.isin()))
                .hasSize(2);
    }

    @Test
    void anInlineInstrumentIsRefusedOnASavingsAccountAnUnknownAccountAndAZeroQuantity() {
        Fixture savings = Fixture.withSavingsAccountAndInstrument(new Instrument(
                UUID.randomUUID(), "Livret", null, "EUR", AssetClass.CASH, PriceSource.MANUAL, null, null));

        assertThatThrownBy(() -> savings.service()
                        .createWithNewInstrument(savings.accountId(), manual("1"), BigDecimal.ONE, null))
                .isInstanceOf(SavingsAccountLineException.class);
        assertThatThrownBy(() ->
                        savings.service().createWithNewInstrument(UUID.randomUUID(), manual("1"), BigDecimal.ONE, null))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> savings.service()
                        .createWithNewInstrument(savings.accountId(), manual("1"), BigDecimal.ZERO, null))
                .isInstanceOf(ZeroQuantityException.class);
    }

    private static final class Fixture {

        private final List<Holding> holdings = new ArrayList<>();
        private final List<UUID> deletedIds = new ArrayList<>();
        private final Map<UUID, Account> accounts;
        private final Map<UUID, Instrument> instruments;
        private final UUID accountId;
        private final UUID instrumentId;
        private final UUID holdingId;
        private final List<Quote> quotes = new ArrayList<>();

        private Fixture(UUID accountId, UUID instrumentId, UUID holdingId) {
            this(accountId, instrumentId, holdingId, AccountType.PEA);
        }

        private Fixture(UUID accountId, UUID instrumentId, UUID holdingId, AccountType type) {
            this.accountId = accountId;
            this.instrumentId = instrumentId;
            this.holdingId = holdingId;
            this.accounts = Map.of(accountId, new Account(accountId, "Account", type, "Bank"));
            this.instruments = new HashMap<>();
            this.instruments.put(
                    instrumentId,
                    new Instrument(
                            instrumentId,
                            "ETF",
                            "FR0011871128",
                            "EUR",
                            AssetClass.ETF,
                            PriceSource.MANUAL,
                            null,
                            null));
        }

        static Fixture withSavingsAccountAndInstrument(Instrument instrument) {
            Fixture fixture = new Fixture(UUID.randomUUID(), instrument.id(), UUID.randomUUID(), AccountType.SAVINGS);
            fixture.instruments.put(instrument.id(), instrument);
            return fixture;
        }

        static Fixture withKnownAccountAndInstrument() {
            return new Fixture(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        }

        static Fixture withExistingHolding() {
            Fixture fixture = withKnownAccountAndInstrument();
            fixture.holdings.add(new Holding(
                    fixture.holdingId, fixture.accountId, fixture.instrumentId, BigDecimal.ONE, null, Instant.EPOCH));
            return fixture;
        }

        static Fixture withExistingCashHolding() {
            Fixture fixture = withKnownAccountAndInstrument();
            fixture.instruments.put(
                    fixture.instrumentId,
                    new Instrument(
                            fixture.instrumentId,
                            "Livret A",
                            null,
                            "EUR",
                            AssetClass.CASH,
                            PriceSource.MANUAL,
                            null,
                            null));
            fixture.holdings.add(new Holding(
                    fixture.holdingId,
                    fixture.accountId,
                    fixture.instrumentId,
                    new BigDecimal("20000"),
                    BigDecimal.ONE,
                    Instant.EPOCH));
            return fixture;
        }

        static Fixture withHolding(String quantity, String averageCost) {
            Fixture fixture = withKnownAccountAndInstrument();
            fixture.holdings.add(new Holding(
                    fixture.holdingId,
                    fixture.accountId,
                    fixture.instrumentId,
                    new BigDecimal(quantity),
                    averageCost == null ? null : new BigDecimal(averageCost),
                    Instant.EPOCH));
            return fixture;
        }

        Instrument addListed(PriceSource source, String sourceRef, String isin) {
            Instrument instrument =
                    new Instrument(UUID.randomUUID(), "Listed", isin, "EUR", AssetClass.ETF, source, sourceRef, null);
            instruments.put(instrument.id(), instrument);
            return instrument;
        }

        Instrument addInstrument(String currency, AssetClass assetClass) {
            Instrument instrument = new Instrument(
                    UUID.randomUUID(), "Other", null, currency, assetClass, PriceSource.MANUAL, null, null);
            instruments.put(instrument.id(), instrument);
            return instrument;
        }

        UUID accountId() {
            return accountId;
        }

        UUID instrumentId() {
            return instrumentId;
        }

        UUID holdingId() {
            return holdingId;
        }

        List<Quote> quotes() {
            return quotes;
        }

        List<Instrument> instruments() {
            return List.copyOf(instruments.values());
        }

        List<UUID> deleted() {
            return deletedIds;
        }

        List<Holding> holdings() {
            return holdings;
        }

        HoldingService service() {
            return new HoldingService(
                    new InMemoryLoadHoldingsPort(),
                    new InMemorySaveHoldingPort(),
                    new InMemoryDeleteHoldingPort(),
                    new InMemoryLoadAccountsPort(),
                    new InMemoryLoadInstrumentsPort(),
                    instrument -> {
                        instruments.put(instrument.id(), instrument);
                        return instrument;
                    },
                    new SaveQuotePort() {
                        @Override
                        public void upsert(Quote quote) {
                            quotes.add(quote);
                        }

                        @Override
                        public void upsertAll(List<Quote> toSave) {
                            quotes.addAll(toSave);
                        }
                    },
                    CLOCK);
        }

        private final class InMemoryLoadHoldingsPort implements LoadHoldingsPort {
            @Override
            public List<Holding> findAll() {
                return List.copyOf(holdings);
            }

            @Override
            public Optional<Holding> findById(UUID id) {
                return holdings.stream().filter(h -> h.id().equals(id)).findFirst();
            }

            @Override
            public Optional<Holding> findByAccountAndInstrument(UUID accountId, UUID instrumentId) {
                return holdings.stream()
                        .filter(h -> h.accountId().equals(accountId)
                                && h.instrumentId().equals(instrumentId))
                        .findFirst();
            }

            @Override
            public List<Holding> findByInstrument(UUID instrumentId) {
                return holdings.stream()
                        .filter(h -> h.instrumentId().equals(instrumentId))
                        .toList();
            }

            @Override
            public List<Holding> findByAccount(UUID accountId) {
                return holdings.stream()
                        .filter(h -> h.accountId().equals(accountId))
                        .toList();
            }
        }

        private final class InMemorySaveHoldingPort implements SaveHoldingPort {
            @Override
            public Holding save(Holding holding) {
                holdings.removeIf(h -> h.id().equals(holding.id()));
                holdings.add(holding);
                return holding;
            }
        }

        private final class InMemoryDeleteHoldingPort implements DeleteHoldingPort {
            @Override
            public void delete(UUID id) {
                holdings.removeIf(h -> h.id().equals(id));
                deletedIds.add(id);
            }
        }

        private final class InMemoryLoadAccountsPort implements LoadAccountsPort {
            @Override
            public List<Account> findAll() {
                return List.copyOf(accounts.values());
            }

            @Override
            public Optional<Account> findById(UUID id) {
                return Optional.ofNullable(accounts.get(id));
            }
        }

        private final class InMemoryLoadInstrumentsPort implements LoadInstrumentsPort {
            @Override
            public List<Instrument> findAll() {
                return List.copyOf(instruments.values());
            }

            @Override
            public Optional<Instrument> findById(UUID id) {
                return Optional.ofNullable(instruments.get(id));
            }

            @Override
            public List<Instrument> findRefreshable(java.util.Set<AssetClass> assetClasses) {
                return List.of();
            }
        }
    }
}
