package com.emoney.cs.common.designpattern.yalco._22_interpreter.ex01;

public class Subtract implements Expression {
    private Expression leftExpression;
    private Expression rightExpression;

    public Subtract(
            Expression leftExpression,
            Expression rightExpression
    ) {
        this.leftExpression = leftExpression;
        this.rightExpression = rightExpression;
    }

    @Override
    public int interpret() {
        return leftExpression.interpret()
                - rightExpression.interpret();
    }
}