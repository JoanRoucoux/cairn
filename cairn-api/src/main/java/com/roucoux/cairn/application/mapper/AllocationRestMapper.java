package com.roucoux.cairn.application.mapper;

import com.roucoux.cairn.domain.model.AccountAllocation;
import com.roucoux.cairn.domain.model.AccountBreakdown;
import com.roucoux.cairn.domain.model.AssetClassAllocation;
import com.roucoux.cairn.domain.model.AssetClassBreakdown;
import com.roucoux.cairn.generated.model.AccountAllocationItemResponse;
import com.roucoux.cairn.generated.model.AccountAllocationResponse;
import com.roucoux.cairn.generated.model.AssetClass;
import com.roucoux.cairn.generated.model.AssetClassAllocationItemResponse;
import com.roucoux.cairn.generated.model.AssetClassAllocationResponse;
import org.springframework.stereotype.Component;

@Component
public class AllocationRestMapper {

    private final AccountRestMapper accountMapper;

    public AllocationRestMapper(AccountRestMapper accountMapper) {
        this.accountMapper = accountMapper;
    }

    public AssetClassAllocationResponse toResponse(AssetClassBreakdown breakdown) {
        AssetClassAllocationResponse response = new AssetClassAllocationResponse();
        response.setTotalEur(PortfolioRestMapper.amount(breakdown.total()));
        response.setItems(breakdown.items().stream().map(this::toItem).toList());
        return response;
    }

    public AccountAllocationResponse toResponse(AccountBreakdown breakdown) {
        AccountAllocationResponse response = new AccountAllocationResponse();
        response.setTotalEur(PortfolioRestMapper.amount(breakdown.total()));
        response.setItems(breakdown.items().stream().map(this::toItem).toList());
        return response;
    }

    private AssetClassAllocationItemResponse toItem(AssetClassAllocation allocation) {
        AssetClassAllocationItemResponse item = new AssetClassAllocationItemResponse();
        item.setAssetClass(AssetClass.valueOf(allocation.assetClass().name()));
        item.setValueEur(PortfolioRestMapper.amount(allocation.value()));
        item.setShare(PortfolioRestMapper.share(allocation.share()));
        item.setLineCount(allocation.lineCount());
        return item;
    }

    private AccountAllocationItemResponse toItem(AccountAllocation allocation) {
        AccountAllocationItemResponse item = new AccountAllocationItemResponse();
        item.setAccount(accountMapper.toResponse(allocation.account()));
        item.setValueEur(PortfolioRestMapper.amount(allocation.value()));
        item.setShare(PortfolioRestMapper.share(allocation.share()));
        item.setLineCount(allocation.lineCount());
        return item;
    }
}
