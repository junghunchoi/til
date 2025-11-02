package com.emoney.cs.common.designpattern.yalco._08_factory_method.ex03;

class FinancialInfo {
    String creditCardNumber;
    String payPalEmail;
    String bankAccountNumber;

    public FinancialInfo(
            String creditCardNumber,
            String payPalEmail,
            String bankAccountNumber
    ) {
        this.creditCardNumber = creditCardNumber;
        this.payPalEmail = payPalEmail;
        this.bankAccountNumber = bankAccountNumber;
    }
}