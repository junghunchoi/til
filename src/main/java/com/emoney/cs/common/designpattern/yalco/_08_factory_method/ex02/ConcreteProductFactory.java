package com.emoney.cs.common.designpattern.yalco._08_factory_method.ex02;

public class ConcreteProductFactory extends ProductFactory {
    @Override
    public Product createProduct(String type) {
        if (type.equalsIgnoreCase("electronics")) {
            return new Electronics();
        } else if (type.equalsIgnoreCase("clothing")) {
            return new Clothing();
        } else if (type.equalsIgnoreCase("book")) {
            return new Book();
        } else {
            throw new IllegalArgumentException("Unknown product type.");
        }
    }
}