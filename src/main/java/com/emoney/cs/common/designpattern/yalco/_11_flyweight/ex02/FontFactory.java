package com.emoney.cs.common.designpattern.yalco._11_flyweight.ex02;

import java.util.HashMap;
import java.util.Map;

// Flyweight Factory class
class FontFactory {
    private static final Map<String, Font> fontMap = new HashMap<>();

    public static Font getFont(String font, int size, String color) {
        String key = font + size + color;
        Font fontObject = fontMap.get(key);

        if (fontObject == null) {
            fontObject = new ConcreteFont(font, size, color);
            fontMap.put(key, fontObject);
            System.out.println("Creating font: " + key);
        } else {
            System.out.println("Reusing font: " + key);
        }
        return fontObject;
    }
}