package com.emoney.cs.common.designpattern.yalco._04_singleton;

public class Main {
    public static void main(String[] args) {
        Button button = new Button("Submit");
        TextField textField = new TextField("Enter your name");
        Label label = new Label("Username");

        button.display();
        textField.display();
        label.display();

        Theme.getInstance().setThemeColor("dark");

        button.display();
        textField.display();
        label.display();
    }
}