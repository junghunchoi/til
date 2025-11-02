package com.emoney.cs.common.designpattern.yalco._25_specification.ex02;

public class PriceSpec implements Specification {
    private int maxPrice;

    public PriceSpec(int maxPrice) {
        this.maxPrice = maxPrice;
    }

    @Override
    public boolean isSatisfiedBy(Product item) {
        return item.getPrice() <= maxPrice;
    }
}