package com.emoney.cs.common.designpattern.yalco._11_flyweight.ex02;

public class Main {
    public static void main(String[] args) {
        Font font1 = FontFactory.getFont("Arial", 12, "Black");
        font1.apply("Hello, World!");

        Font font2 = FontFactory.getFont("Arial", 12, "Black");
        font2.apply("Flyweight Pattern");

        Font font3 = FontFactory.getFont("Times New Roman", 14, "Blue");
        font3.apply("Design Patterns");

        Font font4 = FontFactory.getFont("Arial", 12, "Black");
        font4.apply("Another Text");
    }
}