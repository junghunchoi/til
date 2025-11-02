package com.emoney.cs.common.designpattern.yalco._14_visitor.ex01;

class AreaVisitor implements Visitor {
    @Override
    public void visit(Circle circle) {
        double area
                = Math.PI * circle.getRadius() * circle.getRadius();
        System.out.println("Circle Area: " + area);
    }

    @Override
    public void visit(Rectangle rectangle) {
        double area
                = rectangle.getWidth() * rectangle.getHeight();
        System.out.println("Rectangle Area: " + area);
    }
}