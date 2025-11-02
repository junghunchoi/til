package com.emoney.cs.common.designpattern.yalco._09_proxy.ex02;

// Proxy class
class BankAccountProxy implements BankAccount {
    private RealBankAccount realBankAccount;
    private String userRole;

    public BankAccountProxy(String userRole, double initialBalance) {
        this.userRole = userRole;
        this.realBankAccount = new RealBankAccount(initialBalance);
    }

    // Check if the user has Admin access
    private boolean hasAccess() {
        return "Admin".equalsIgnoreCase(userRole);
    }

    @Override
    public void withdraw(double amount) {
        if (hasAccess()) {
            realBankAccount.withdraw(amount);
        } else {
            System.out.println("Access denied. Only Admin can withdraw.");
        }
    }

    @Override
    public void deposit(double amount) {
        realBankAccount.deposit(amount);
    }
}