package com.emoney.cs.common.designpattern.yalco._22_interpreter.ex01;

public class Add implements Expression {
    private Expression leftExpression;
    private Expression rightExpression;

    public Add(
            Expression leftExpression,
            Expression rightExpression
    ) {
        this.leftExpression = leftExpression;
        this.rightExpression = rightExpression;
    }

    @Override
    public int interpret() {
        return leftExpression.interpret()
                + rightExpression.interpret();
    }
}