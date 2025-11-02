package com.emoney.cs.common.designpattern.yalco._24_iterator.ex02;

import java.util.*;

// BreadthFirstIterator class
class BreadthFirstIterator implements FileSystemIterator {
    private Queue<FileSystemItem> queue = new LinkedList<>();

    public BreadthFirstIterator(Directory root) {
        queue.offer(root);
    }

    @Override
    public boolean hasNext() {
        return !queue.isEmpty();
    }

    @Override
    public FileSystemItem next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }

        FileSystemItem current = queue.poll();
        if (current instanceof Directory) {
            queue.addAll(((Directory) current).getContents());
        }
        return current;
    }
}