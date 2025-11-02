package com.emoney.cs.common.designpattern.yalco._25_specification.ex01;

public interface Specification {
    boolean isSatisfiedBy(int number);

    default Specification and(Specification other) {
        return number -> this.isSatisfiedBy(number) && other.isSatisfiedBy(number);
    }
}
