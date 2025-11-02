package com.emoney.cs.common.designpattern.yalco._08_factory_method.ex02;

public class Clothing implements Product {
    @Override
    public void create() {
        System.out.println("Clothing product created.");
    }
}