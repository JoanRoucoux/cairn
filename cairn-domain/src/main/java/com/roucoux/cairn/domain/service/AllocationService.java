package com.roucoux.cairn.domain.service;

import com.roucoux.cairn.domain.model.Account;
import com.roucoux.cairn.domain.model.AccountAllocation;
import com.roucoux.cairn.domain.model.AccountBreakdown;
import com.roucoux.cairn.domain.model.Allocation;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.AssetClassAllocation;
import com.roucoux.cairn.domain.model.AssetClassBreakdown;
import com.roucoux.cairn.domain.model.Portfolio;
import com.roucoux.cairn.domain.model.ValuedHolding;
import com.roucoux.cairn.domain.port.in.GetAccountAllocationUseCase;
import com.roucoux.cairn.domain.port.in.GetAssetClassAllocationUseCase;
import com.roucoux.cairn.domain.port.in.GetPortfolioUseCase;
import java.util.function.Predicate;

public class AllocationService implements GetAssetClassAllocationUseCase, GetAccountAllocationUseCase {

    private final GetPortfolioUseCase getPortfolio;

    public AllocationService(GetPortfolioUseCase getPortfolio) {
        this.getPortfolio = getPortfolio;
    }

    @Override
    public AssetClassBreakdown byAssetClass() {
        Portfolio portfolio = getPortfolio.get();
        return new AssetClassBreakdown(
                portfolio.total(),
                portfolio.byAssetClass().stream()
                        .map(allocation -> {
                            AssetClass assetClass = AssetClass.valueOf(allocation.label());
                            return new AssetClassAllocation(
                                    assetClass,
                                    allocation.value(),
                                    allocation.share(),
                                    valuedLinesOf(
                                            portfolio, line -> line.instrument().assetClass() == assetClass));
                        })
                        .toList());
    }

    @Override
    public AccountBreakdown byAccount() {
        Portfolio portfolio = getPortfolio.get();
        return new AccountBreakdown(
                portfolio.total(),
                portfolio.byAccount().stream()
                        .map(allocation -> {
                            Account account = accountNamed(portfolio, allocation);
                            return new AccountAllocation(
                                    account,
                                    allocation.value(),
                                    allocation.share(),
                                    valuedLinesOf(
                                            portfolio,
                                            line -> line.account().name().equals(account.name())));
                        })
                        .toList());
    }

    private static Account accountNamed(Portfolio portfolio, Allocation allocation) {
        return portfolio.holdings().stream()
                .map(ValuedHolding::account)
                .filter(account -> account.name().equals(allocation.label()))
                .findFirst()
                .orElseThrow();
    }

    private static int valuedLinesOf(Portfolio portfolio, Predicate<ValuedHolding> in) {
        return (int) portfolio.holdings().stream()
                .filter(line -> line.marketValue().isPresent())
                .filter(in)
                .count();
    }
}
