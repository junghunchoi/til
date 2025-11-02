package com.emoney.cs.common.designpattern.yalco._14_visitor.ex01;

interface Visitor {
    void visit(Circle circle);
    void visit(Rectangle rectangle);
}