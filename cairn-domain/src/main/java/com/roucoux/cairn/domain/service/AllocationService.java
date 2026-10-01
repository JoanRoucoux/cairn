package com.roucoux.cairn.domain.service;

import com.roucoux.cairn.domain.model.Account;
import com.roucoux.cairn.domain.model.AccountAllocation;
import com.roucoux.cairn.domain.model.AccountBreakdown;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.AssetClassAllocation;
import com.roucoux.cairn.domain.model.AssetClassBreakdown;
import com.roucoux.cairn.domain.model.Portfolio;
import com.roucoux.cairn.domain.model.ValuedHolding;
import com.roucoux.cairn.domain.port.in.GetAccountAllocationUseCase;
import com.roucoux.cairn.domain.port.in.GetAssetClassAllocationUseCase;
import com.roucoux.cairn.domain.port.in.GetPortfolioUseCase;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AllocationService implements GetAssetClassAllocationUseCase, GetAccountAllocationUseCase {

    private final GetPortfolioUseCase getPortfolio;

    public AllocationService(GetPortfolioUseCase getPortfolio) {
        this.getPortfolio = getPortfolio;
    }

    @Override
    public AssetClassBreakdown byAssetClass() {
        Portfolio portfolio = getPortfolio.get();
        Map<AssetClass, Integer> lineCounts =
                lineCounts(portfolio, line -> line.instrument().assetClass());
        return new AssetClassBreakdown(
                portfolio.total(),
                portfolio.byAssetClass().stream()
                        .map(allocation -> {
                            AssetClass assetClass = AssetClass.valueOf(allocation.label());
                            return new AssetClassAllocation(
                                    assetClass, allocation.value(), allocation.share(), lineCounts.get(assetClass));
                        })
                        .toList());
    }

    @Override
    public AccountBreakdown byAccount() {
        Portfolio portfolio = getPortfolio.get();
        Map<Account, Integer> lineCounts = lineCounts(portfolio, ValuedHolding::account);
        Map<String, Account> accounts = new LinkedHashMap<>();
        lineCounts.keySet().forEach(account -> accounts.put(account.name(), account));
        return new AccountBreakdown(
                portfolio.total(),
                portfolio.byAccount().stream()
                        .map(allocation -> {
                            Account account = accounts.get(allocation.label());
                            return new AccountAllocation(
                                    account, allocation.value(), allocation.share(), lineCounts.get(account));
                        })
                        .toList());
    }

    private static <K> Map<K, Integer> lineCounts(Portfolio portfolio, Function<ValuedHolding, K> by) {
        return portfolio.holdings().stream().collect(Collectors.toMap(by, line -> 1, Integer::sum, LinkedHashMap::new));
    }
}
