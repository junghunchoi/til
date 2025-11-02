package com.emoney.cs.common.designpattern.yalco._08_factory_method.ex03;

class BankTransferPayment implements Payment {
    private String bankAccountNumber;

    public BankTransferPayment(String bankAccountNumber) {
        this.bankAccountNumber = bankAccountNumber;
    }

    @Override
    public void processPayment(double amount) {
        System.out.println("Bank transfer: $" + amount);
    }
}