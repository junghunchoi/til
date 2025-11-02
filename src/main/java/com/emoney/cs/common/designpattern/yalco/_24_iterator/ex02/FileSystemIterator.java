package com.emoney.cs.common.designpattern.yalco._24_iterator.ex02;

// FileSystemIterator interface
interface FileSystemIterator {
    boolean hasNext();
    FileSystemItem next();
}