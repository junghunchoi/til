package com.emoney.cs.common.designpattern.yalco._08_factory_method.ex03;

// Client code
public class Main {
    public static void main(String[] args) {
        FinancialInfo userInfo = new FinancialInfo(
                "1234-5678-9012-3456", "user@example.com", "987654321"
        );

        PaymentFactory factory = new CreditCardPaymentFactory();
        Payment payment = factory.createPayment(userInfo);
        payment.processPayment(100.0);

        factory = new PayPalPaymentFactory();
        payment = factory.createPayment(userInfo);
        payment.processPayment(200.0);

        factory = new BankTransferPaymentFactory();
        payment = factory.createPayment(userInfo);
        payment.processPayment(300.0);
    }
}