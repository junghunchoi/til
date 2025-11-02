package com.emoney.cs.common.designpattern.yalco._08_factory_method.ex02;

public class Main {
    public static void main(String[] args) {
        ProductFactory factory = new ConcreteProductFactory();

        // Create an electronics product
        Product electronics = factory.orderProduct("electronics");

        // Create a clothing product
        Product clothing = factory.orderProduct("clothing");

        // Create a book product
        Product book = factory.orderProduct("book");
    }
}