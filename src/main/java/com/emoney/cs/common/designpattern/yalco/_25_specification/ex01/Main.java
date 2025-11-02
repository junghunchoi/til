package com.emoney.cs.common.designpattern.yalco._25_specification.ex01;

public class Main {
    public static void main(String[] args) {
        Specification evenSpec = new EvenSpecification();
        Specification rangeSpec = new RangeSpecification(10, 20);

        Specification evenAndInRangeSpec = evenSpec.and(rangeSpec);

        int number = 24;

        System.out.println("Even: " + evenSpec.isSatisfiedBy(number));
        System.out.println("In range 10-20: " + rangeSpec.isSatisfiedBy(number));
        System.out.println("Even and in range 10-20: " + evenAndInRangeSpec.isSatisfiedBy(number));
    }
}
