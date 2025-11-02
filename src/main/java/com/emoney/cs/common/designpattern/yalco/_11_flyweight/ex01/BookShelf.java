package com.emoney.cs.common.designpattern.yalco._11_flyweight.ex01;

import java.util.HashMap;
import java.util.Map;

// FlyweightFactory class
class Bookshelf {
    private static final Map<String, Book> bookshelf = new HashMap<>();

    public static Book getBook(String title) {
        Book book = bookshelf.get(title);

        if (book == null) {
            book = new Book(title);
            bookshelf.put(title, book);
            System.out.println(
                    "Adding a new book to the bookshelf: " + title);
        } else {
            System.out.println(
                    "Reusing existing book from the bookshelf: " + title);
        }
        return book;
    }
}