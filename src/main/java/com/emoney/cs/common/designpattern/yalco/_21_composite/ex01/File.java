package com.emoney.cs.common.designpattern.yalco._21_composite.ex01;

// Leaf
class File implements FileSystemComponent {
    private String name;
    private int size;

    public File(String name, int size) {
        this.name = name;
        this.size = size;
    }

    @Override
    public void printName() {
        System.out.println("File: " + name);
    }

    @Override
    public int getSize() {
        return size;
    }

    @Override
    public String getName() {
        return name;
    }
}