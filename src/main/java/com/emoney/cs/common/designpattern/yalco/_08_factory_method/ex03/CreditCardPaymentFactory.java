package com.emoney.cs.common.designpattern.yalco._08_factory_method.ex03;

class CreditCardPaymentFactory extends PaymentFactory {
    @Override
    Payment createPayment(FinancialInfo info) {
        return new CreditCardPayment(info.creditCardNumber);
    }
}