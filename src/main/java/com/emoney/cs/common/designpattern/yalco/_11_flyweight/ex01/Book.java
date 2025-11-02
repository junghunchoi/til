package com.emoney.cs.common.designpattern.yalco._11_flyweight.ex01;

// Flyweight class
class Book {
    private final String title; // intrinsic state (shared)

    public Book(String title) {
        this.title = title;
    }

    public void read() {
        System.out.println("Reading the book titled: " + title);
    }
}