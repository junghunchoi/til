package com.emoney.cs.common.designpattern.yalco._25_specification.ex02;

import java.util.List;
import java.util.stream.Collectors;

public class ProductFilter {
    public static List<Product> filter(List<Product> items, Specification spec) {
        return items.stream()
                .filter(spec::isSatisfiedBy)
                .collect(Collectors.toList());
    }

    public static void printProducts(List<Product> products) {
        products.forEach(p -> System.out.println(p.getName() + " - " + p.getCategory() + " - $" + p.getPrice() + " - Stock: " + p.getStock()));
    }
}
