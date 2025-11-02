package com.emoney.cs.common.designpattern.yalco._09_proxy.ex02;

// Client code
public class Main {
    public static void main(String[] args) {
        // User with Admin access
        BankAccount adminAccount = new BankAccountProxy("Admin", 1000);
        adminAccount.deposit(500);   // Deposit allowed
        adminAccount.withdraw(300);  // Withdraw allowed

        // User without Admin access
        BankAccount userAccount = new BankAccountProxy("User", 1000);
        userAccount.deposit(500);    // Deposit allowed
        userAccount.withdraw(300);   // Withdraw denied
    }
}
