package com.emoney.cs.common.designpattern.yalco._11_flyweight.ex01;

// Client code
public class Main {
    public static void main(String[] args) {
        Book book1 = Bookshelf.getBook("Effective Java");
        book1.read();

        Book book2 = Bookshelf.getBook("Effective Java");
        book2.read();

        Book book3 = Bookshelf.getBook("Clean Code");
        book3.read();

        // Check if book1 and book2 are the same object
        System.out.println(
                book1 == book2 ? "Same book for 'Effective Java'."
                        : "Different books for 'Effective Java'."
        );
    }
}