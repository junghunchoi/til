package com.emoney.cs.common.designpattern.yalco._21_composite.ex02;

import java.util.ArrayList;
import java.util.List;

class Window implements UIComponent {
    private String title;
    private List<UIComponent> components
            = new ArrayList<>();

    public Window(String title) {
        this.title = title;
    }

    @Override
    public void render() {
        System.out.println("Window: " + title);
        components.forEach(UIComponent::render);
    }

    @Override
    public void add(UIComponent component) {
        components.add(component);
    }

    @Override
    public void remove(UIComponent component) {
        components.remove(component);
    }
}