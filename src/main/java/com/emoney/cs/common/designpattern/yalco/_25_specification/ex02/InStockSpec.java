package com.emoney.cs.common.designpattern.yalco._25_specification.ex02;

public class InStockSpec implements Specification {
    @Override
    public boolean isSatisfiedBy(Product item) {
        return item.getStock() > 0;
    }
}