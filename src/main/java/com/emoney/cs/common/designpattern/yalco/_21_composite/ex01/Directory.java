package com.emoney.cs.common.designpattern.yalco._21_composite.ex01;

import java.util.List;
import java.util.ArrayList;

// Composite
class Directory implements FileSystemComponent {
    private String name;
    private List<FileSystemComponent> components = new ArrayList<>();

    public Directory(String name) {
        this.name = name;
    }

    public void add(FileSystemComponent component) {
        components.add(component);
    }

    public void remove(FileSystemComponent component) {
        components.remove(component);
    }

    public void remove(String name) {
        components.removeIf(
                component -> component.getName().equals(name));
    }

    @Override
    public void printName() {
        System.out.println("Directory: " + name);
        for (FileSystemComponent component : components) {
            component.printName();
        }
    }

    @Override
    public int getSize() {
        int totalSize = 0;
        for (FileSystemComponent component : components) {
            totalSize += component.getSize();
        }
        return totalSize;
    }

    @Override
    public String getName() {
        return name;
    }
}