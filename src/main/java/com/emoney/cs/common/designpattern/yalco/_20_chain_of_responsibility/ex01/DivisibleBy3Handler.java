package com.emoney.cs.common.designpattern.yalco._20_chain_of_responsibility.ex01;

class DivisibleBy3Handler extends Handler {
    @Override
    protected void process(int number) {
        if (number % 3 == 0) {
            System.out.println(number + " is divible by 3");
        }
    }
}