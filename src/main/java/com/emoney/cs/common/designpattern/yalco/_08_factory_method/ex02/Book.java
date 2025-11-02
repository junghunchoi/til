package com.emoney.cs.common.designpattern.yalco._08_factory_method.ex02;

public class Book implements Product {
    @Override
    public void create() {
        System.out.println("Book product created.");
    }
}