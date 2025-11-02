package com.emoney.cs.common.designpattern.yalco._25_specification.ex02;

import java.util.Arrays;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        List<Product> products = Arrays.asList(
                new Product("Laptop", "Electronics", 1200, 5),
                new Product("Smartphone", "Electronics", 800, 0),
                new Product("Headphones", "Electronics", 200, 10),
                new Product("Book", "Literature", 20, 50)
        );

        Specification electronicsSpec = new CategorySpec("Electronics");
        Specification inStockSpec = new InStockSpec();
        Specification expensiveSpec = new PriceSpec(500);

        Specification electronicInStock = new AndSpec(electronicsSpec, inStockSpec);
        Specification electronicOrInStock = new OrSpec(electronicsSpec, inStockSpec);
        Specification notExpensive = new NotSpec(expensiveSpec);

        System.out.println("Electronics in stock:");
        ProductFilter.printProducts(ProductFilter.filter(products, electronicInStock));

        System.out.println("\nElectronics or items in stock:");
        ProductFilter.printProducts(ProductFilter.filter(products, electronicOrInStock));

        System.out.println("\nNot expensive items:");
        ProductFilter.printProducts(ProductFilter.filter(products, notExpensive));
    }
}