package com.emoney.cs.common.designpattern.yalco._20_chain_of_responsibility.ex01;

public class Main {
    public static void main(String[] args) {
        Handler positive = new PositiveHandler();
        Handler even = new EvenHandler();
        Handler divisibleBy3 = new DivisibleBy3Handler();

        positive.setNext(even);
        even.setNext(divisibleBy3);

        positive.handle(-2);
        positive.handle(4);
        positive.handle(6);
    }
}