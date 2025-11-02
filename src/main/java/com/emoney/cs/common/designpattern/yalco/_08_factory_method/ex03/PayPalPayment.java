package com.emoney.cs.common.designpattern.yalco._08_factory_method.ex03;

class PayPalPayment implements Payment {
    private String payPalEmail;

    public PayPalPayment(String payPalEmail) {
        this.payPalEmail = payPalEmail;
    }

    @Override
    public void processPayment(double amount) {
        System.out.println("PayPal: $" + amount);
    }
}