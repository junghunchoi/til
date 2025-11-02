package com.emoney.cs.common.designpattern.yalco._16_decorator.ex01;

// ConcreteComponent class
class SimpleCoffee implements Coffee {
    @Override
    public String getDescription() {
        return "Simple coffee";
    }

    @Override
    public double getCost() {
        return 5.0;
    }
}