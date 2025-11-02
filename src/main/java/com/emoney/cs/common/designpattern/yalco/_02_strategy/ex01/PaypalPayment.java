package com.emoney.cs.common.designpattern.yalco._02_strategy.ex01;

class PayPalPayment implements PaymentStrategy {
    private String email;

    public PayPalPayment(String email) {
        this.email = email;
    }

    @Override
    public void pay(int amount) {
        System.out.println(amount + " paid using PayPal");
    }
}