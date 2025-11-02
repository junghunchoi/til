package com.emoney.cs.common.designpattern.yalco._22_interpreter.ex01;

import java.lang.Number;

public class Main {
    public static void main(String[] args) {
        // (5 + 2) - 3
        Expression five = new java.lang.Number(5);
        Expression two = new java.lang.Number(2);
        Expression three = new Number(3);

        Expression addExpression = new Add(five, two);
        // 5 + 2

        Expression subtractExpression
                = new Subtract(addExpression, three);
        // (5 + 2) - 3

        System.out.println(
                "(5 + 2) - 3 = "
                        + subtractExpression.interpret());
    }
}