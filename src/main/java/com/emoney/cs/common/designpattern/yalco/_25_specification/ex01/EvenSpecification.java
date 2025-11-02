package com.emoney.cs.common.designpattern.yalco._25_specification.ex01;

class EvenSpecification implements Specification {
    @Override
    public boolean isSatisfiedBy(int number) {
        return number % 2 == 0;
    }
}
