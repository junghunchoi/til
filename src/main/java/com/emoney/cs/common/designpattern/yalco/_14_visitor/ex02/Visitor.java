package com.emoney.cs.common.designpattern.yalco._14_visitor.ex02;

interface Visitor {
    void visit(File file);
    void visit(Directory directory);
}