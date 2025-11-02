package com.emoney.cs.common.designpattern.yalco._14_visitor.ex02;

public class Main {

    public static void main(String[] args) {
        // Create files
        File file1 = new File("file1.txt", 100);
        File file2 = new File("file2.txt", 200);
        File file3 = new File("file3.txt", 300);

        // Create directories and add files to them
        Directory dir1 = new Directory("Folder1");
        dir1.addElement(file1);
        dir1.addElement(file2);

        Directory dir2 = new Directory("Folder2");
        dir2.addElement(file3);

        Directory rootDir = new Directory("Root");
        rootDir.addElement(dir1);
        rootDir.addElement(dir2);

        // Calculate total size
        SizeCalculatorVisitor sizeVisitor
                = new SizeCalculatorVisitor();
        rootDir.accept(sizeVisitor);
        System.out.println(
                "Total size of file system: "
                        + sizeVisitor.getTotalSize() + " bytes");

        // Search for a specific file
        FileSearchVisitor searchVisitor
                = new FileSearchVisitor("file3.txt");
        rootDir.accept(searchVisitor);
        File foundFile = searchVisitor.getFoundFile();
        if (foundFile != null) {
            System.out.println(
                    "File found: " + foundFile.getName()
                            + ", Size: " + foundFile.getSize()
                            + " bytes");
        } else {
            System.out.println("File not found.");
        }
    }
}
