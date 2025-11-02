package com.emoney.cs.common.designpattern.yalco._08_factory_method.ex03;
class CreditCardPayment implements Payment {
    private String creditCardNumber;

    public CreditCardPayment(String creditCardNumber) {
        this.creditCardNumber = creditCardNumber;
    }

    @Override
    public void processPayment(double amount) {
        System.out.println("Credit card: $" + amount);
    }
}