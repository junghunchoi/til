package com.emoney.cs.common.designpattern.yalco._22_interpreter.ex01;

public class Number implements Expression {
    private int number;

    public Number(int number) {
        this.number = number;
    }

    @Override
    public int interpret() {
        return this.number;
    }
}