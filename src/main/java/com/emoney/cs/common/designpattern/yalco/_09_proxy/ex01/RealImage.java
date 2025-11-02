package com.emoney.cs.common.designpattern.yalco._09_proxy.ex01;

// RealSubject class
class RealImage implements Image {
    private String fileName;

    public RealImage(String fileName) {
        this.fileName = fileName;
        loadFromDisk();
    }

    private void loadFromDisk() {
        System.out.println("Loading " + fileName);
    }

    @Override
    public void display() {
        System.out.println("Displaying " + fileName);
    }

    @Override
    public String getFileName() {
        return fileName;
    }
}
