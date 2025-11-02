package com.emoney.cs.common.designpattern.yalco._21_composite.ex01;

// Client
public class Main {
    public static void main(String[] args) {
        File file1 = new File("Document.txt", 100);
        File file2 = new File("Image.jpg", 200);

        Directory subDir = new Directory("SubFolder");
        subDir.add(new File("SubFile.txt", 50));

        Directory rootDir = new Directory("RootFolder");
        rootDir.add(file1);
        rootDir.add(file2);
        rootDir.add(subDir);

        System.out.println("Initial structure:");
        rootDir.printName();
        System.out.println("Total size: " + rootDir.getSize() + " KB");

        System.out.println("\nRemoving Image.jpg:");
        rootDir.remove("Image.jpg");
        rootDir.printName();
        System.out.println("Total size: " + rootDir.getSize() + " KB");

        System.out.println("\nRemoving SubFolder:");
        rootDir.remove(subDir);
        rootDir.printName();
        System.out.println("Total size: " + rootDir.getSize() + " KB");
    }
}