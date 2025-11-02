package com.emoney.cs.common.designpattern.yalco._24_iterator.ex02;

// FileSystem class
class FileSystem {
    private Directory root;

    public FileSystem(Directory root) {
        this.root = root;
    }

    public FileSystemIterator depthFirstIterator() {
        return new DepthFirstIterator(root);
    }

    public FileSystemIterator breadthFirstIterator() {
        return new BreadthFirstIterator(root);
    }
}