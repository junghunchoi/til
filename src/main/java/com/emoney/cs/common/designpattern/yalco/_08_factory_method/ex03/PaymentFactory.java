package com.emoney.cs.common.designpattern.yalco._08_factory_method.ex03;

abstract class PaymentFactory {
    abstract Payment createPayment(FinancialInfo info);
}