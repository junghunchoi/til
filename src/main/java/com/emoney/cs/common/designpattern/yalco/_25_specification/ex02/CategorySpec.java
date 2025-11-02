package com.emoney.cs.common.designpattern.yalco._25_specification.ex02;

public class CategorySpec implements Specification {
    private String category;

    public CategorySpec(String category) {
        this.category = category;
    }

    @Override
    public boolean isSatisfiedBy(Product item) {
        return item.getCategory().equals(category);
    }
}