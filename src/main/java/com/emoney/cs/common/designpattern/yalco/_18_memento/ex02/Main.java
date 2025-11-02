package com.emoney.cs.common.designpattern.yalco._18_memento.ex02;

public class Main {
    public static void main(String[] args) {
        Editor editor = new Editor();

        editor.write("Hello, ");
        editor.write("this is Memento pattern. ");
        System.out.println(editor.getContent());

        // Output: Hello, this is Memento pattern.

        editor.undo();
        System.out.println(editor.getContent());
        // Output: Hello,

        editor.write("This is an example implemented in Java.");
        System.out.println(editor.getContent());

        // Output: Hello, This is an example implemented in Java.
    }
}