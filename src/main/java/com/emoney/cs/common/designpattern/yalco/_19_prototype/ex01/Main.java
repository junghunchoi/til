package com.emoney.cs.common.designpattern.yalco._19_prototype.ex01;

public class Main {
    public static void main(String[] args) {
        Person original = new Person("John", 30, "123 Main St");
        original.displayInfo();

        Person cloned = original.clone();
        cloned.setAddress("456 Clone St");

        System.out.println("\nAfter cloning and modifying the clone:");
        original.displayInfo();
        cloned.displayInfo();
    }
}