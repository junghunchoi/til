package com.emoney.cs.common.designpattern.yalco._01_facade.ex02;

// Client code demonstrating the use of FileSystemFacade
public class Main {
    public static void main(String[] args) {
        FileSystemFacade fs = new FileSystemFacade();

        // Write to file
        boolean writeSuccess = fs.writeFile(
                "test.txt", "Hello, Facade Pattern!"
        );
        System.out.println("File write success: " + writeSuccess);

        // Read from file
        String content = fs.readFile("test.txt");
        System.out.println("File content: " + content);

        // Delete file
        boolean deleteSuccess = fs.deleteFile("test.txt");
        System.out.println(
                "File delete success: " + deleteSuccess
        );
    }
}