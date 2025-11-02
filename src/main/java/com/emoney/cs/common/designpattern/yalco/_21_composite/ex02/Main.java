package com.emoney.cs.common.designpattern.yalco._21_composite.ex02;

public class Main {
    public static void main(String[] args) {
        Button submitButton = new Button("Submit");
        Button cancelButton = new Button("Cancel");
        TextBox usernameField = new TextBox("Username");

        Panel formPanel = new Panel("Form");
        formPanel.add(submitButton);
        formPanel.add(cancelButton);
        formPanel.add(usernameField);

        Window mainWindow = new Window("Main");
        mainWindow.add(formPanel);
        mainWindow.render();

        System.out.println();

        formPanel.remove(cancelButton);
        mainWindow.render();
    }
}
