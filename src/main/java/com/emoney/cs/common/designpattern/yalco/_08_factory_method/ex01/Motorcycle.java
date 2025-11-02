package com.emoney.cs.common.designpattern.yalco._08_factory_method.ex01;

class Motorcycle implements Vehicle {
    @Override
    public void drive() {
        System.out.println("Riding a motorcycle");
    }
}