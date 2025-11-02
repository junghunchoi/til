package com.emoney.cs.common.designpattern.yalco._21_composite.ex02;

import java.util.ArrayList;
import java.util.List;

class Panel implements UIComponent {
    private String name;
    private List<UIComponent> components
            = new ArrayList<>();

    public Panel(String name) {
        this.name = name;
    }

    @Override
    public void render() {
        System.out.println("Panel: " + name);
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