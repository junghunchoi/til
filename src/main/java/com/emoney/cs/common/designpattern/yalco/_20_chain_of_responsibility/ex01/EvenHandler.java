package com.emoney.cs.common.designpattern.yalco._20_chain_of_responsibility.ex01;

class EvenHandler extends Handler {
    @Override
    protected void process(int number) {
        if (number % 2 == 0) {
            System.out.println(number + " is an even number");
        }
    }
}