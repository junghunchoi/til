package com.emoney.cs.common.designpattern.yalco._16_decorator.ex02;

// Main class to demonstrate the usage
public class Main {
    public static void main(String[] args) {
        // Create a plain text
        Text text = new PlainText("Hello, Decorator Pattern!");
        System.out.println("Plain text: " + text.getContent());

        text = new BoldDecorator(text);
        System.out.println("Bold text: " + text.getContent());

        text = new ItalicDecorator(text);
        System.out.println("Bold and italic text: " + text.getContent());

        text = new UnderlineDecorator(text);
        System.out.println(
                "Bold, italic, and underlined text: " + text.getContent()
        );

        Text anotherText = new UnderlineDecorator(
                new ItalicDecorator(
                        new PlainText("Another example")
                )
        );
        System.out.println(
                "Underlined and italic text: " + anotherText.getContent()
        );
    }
}