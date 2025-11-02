package com.emoney.cs.common.designpattern.yalco._24_iterator.ex02;

// Main class
public class Main {
    public static void main(String[] args) {
        Directory root = new Directory("root");
        Directory home = new Directory("home");
        Directory user = new Directory("user");
        File file1 = new File("file1.txt");
        File file2 = new File("file2.txt");
        File file3 = new File("file3.txt");

        root.add(home);
        home.add(user);
        user.add(file1);
        user.add(file2);
        home.add(file3);

        FileSystem fileSystem = new FileSystem(root);

        System.out.println("Depth-First Traversal:");
        FileSystemIterator depthIterator
                = fileSystem.depthFirstIterator();
        while (depthIterator.hasNext()) {
            System.out.println(depthIterator.next().getName());
        }

        System.out.println("\nBreadth-First Traversal:");
        FileSystemIterator breadthIterator
                = fileSystem.breadthFirstIterator();
        while (breadthIterator.hasNext()) {
            System.out.println(breadthIterator.next().getName());
        }
    }
}