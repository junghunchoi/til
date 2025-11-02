package com.emoney.cs.common.designpattern.yalco._25_specification.ex02;

public class NotSpec implements Specification {
    private Specification spec;

    public NotSpec(Specification spec) {
        this.spec = spec;
    }

    @Override
    public boolean isSatisfiedBy(Product item) {
        return !spec.isSatisfiedBy(item);
    }
}