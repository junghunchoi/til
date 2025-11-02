package com.emoney.cs.common.designpattern.yalco._20_chain_of_responsibility.ex01;

class PositiveHandler extends Handler {
    @Override
    protected void process(int number) {
        if (number > 0) {
            System.out.println(number + " is a positive number");
        }
    }
}