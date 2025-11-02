package com.emoney.cs.common.designpattern.yalco._09_proxy.ex02;

// Subject interface
interface BankAccount {
    void withdraw(double amount);
    void deposit(double amount);
}