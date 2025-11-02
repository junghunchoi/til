package com.emoney.cs.common.designpattern.yalco._24_iterator.ex02;

// File class
class File implements FileSystemItem {
    private String name;

    public File(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }
}