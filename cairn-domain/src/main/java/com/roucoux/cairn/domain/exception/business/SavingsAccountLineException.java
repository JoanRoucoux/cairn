package com.roucoux.cairn.domain.exception.business;

public class SavingsAccountLineException extends BusinessException {

    public SavingsAccountLineException() {
        super("A savings account holds one balance, not lines");
    }
}
