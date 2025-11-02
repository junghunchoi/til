package com.emoney.cs.common.designpattern.yalco._14_visitor.ex01;

public class Main {
    public static void main(String[] args) {
        Shape circle = new Circle(5);
        Shape rectangle = new Rectangle(4, 6);

        Visitor areaVisitor = new AreaVisitor();
        Visitor perimeterVisitor = new PerimeterVisitor();

        System.out.println("Calculating Area:");
        circle.accept(areaVisitor);
        rectangle.accept(areaVisitor);

        System.out.println("\nCalculating Perimeter:");
        circle.accept(perimeterVisitor);
        rectangle.accept(perimeterVisitor);
    }
}