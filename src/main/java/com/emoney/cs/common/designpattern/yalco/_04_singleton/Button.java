package com.emoney.cs.common.designpattern.yalco._04_singleton;

public class Button {
    private String label;

    public Button(String label) {
        this.label = label;
    }

    public void display() {
        String themeColor = Theme.getInstance().getThemeColor();
        System.out.println(
                "Button [" + label + "] displayed in " + themeColor + " theme."
        );
    }
}