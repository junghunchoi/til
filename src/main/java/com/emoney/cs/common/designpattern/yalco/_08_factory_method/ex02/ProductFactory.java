package com.emoney.cs.common.designpattern.yalco._08_factory_method.ex02;

public abstract class ProductFactory {
    // Factory Method
    public abstract Product createProduct(String type);

    public Product orderProduct(String type) {
        Product product = createProduct(type); // Delegate object creation
        product.create();
        return product;
    }
}