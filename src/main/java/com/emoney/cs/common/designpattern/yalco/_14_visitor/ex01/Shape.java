package com.emoney.cs.common.designpattern.yalco._14_visitor.ex01;

// Element interface
interface Shape {
    void accept(Visitor visitor);
}