package com.emoney.cs.common.designpattern.yalco._14_visitor.ex02;

class SizeCalculatorVisitor implements Visitor {
    private long totalSize = 0;

    @Override
    public void visit(File file) {
        totalSize += file.getSize();
    }

    @Override
    public void visit(Directory directory) {
        for (FileSystemElement element : directory.getElements()) {
            element.accept(this);
        }
    }

    public long getTotalSize() {
        return totalSize;
    }
}