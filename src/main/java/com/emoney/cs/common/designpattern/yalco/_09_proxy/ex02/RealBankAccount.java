package com.emoney.cs.common.designpattern.yalco._09_proxy.ex02;

// Real Subject class
class RealBankAccount implements BankAccount {
    private double balance;

    public RealBankAccount(double initialBalance) {
        this.balance = initialBalance;
    }

    @Override
    public void withdraw(double amount) {
        if (balance >= amount) {
            balance -= amount;
            System.out.println(amount + " withdrawn. Current balance: " + balance);
        } else {
            System.out.println("Insufficient balance.");
        }
    }

    @Override
    public void deposit(double amount) {
        balance += amount;
        System.out.println(amount + " deposited. Current balance: " + balance);
    }
}