package com.emoney.cs.common.designpattern.yalco._04_singleton;

public class Label {
    private String text;

    public Label(String text) {
        this.text = text;
    }

    public void display() {
        String themeColor = Theme.getInstance().getThemeColor();
        System.out.println(
                "Label [" + text + "] displayed in " + themeColor + " theme."
        );
    }
}