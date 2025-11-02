package com.emoney.cs.common.designpattern.yalco._24_iterator.ex02;

import java.util.*;

// Directory class
class Directory implements FileSystemItem {
    private String name;
    private List<FileSystemItem> contents = new ArrayList<>();

    public Directory(String name) {
        this.name = name;
    }

    public void add(FileSystemItem item) {
        contents.add(item);
    }

    public List<FileSystemItem> getContents() {
        return contents;
    }

    @Override
    public String getName() {
        return name;
    }
}