package com.emoney.cs.common.designpattern.yalco._14_visitor.ex02;

class FileSearchVisitor implements Visitor {
    private String searchFileName;
    private File foundFile;

    public FileSearchVisitor(String searchFileName) {
        this.searchFileName = searchFileName;
    }

    @Override
    public void visit(File file) {
        if (file.getName().equals(searchFileName)) {
            foundFile = file;
        }
    }

    @Override
    public void visit(Directory directory) {
        for (FileSystemElement element : directory.getElements()) {
            element.accept(this);
        }
    }

    public File getFoundFile() {
        return foundFile;
    }
}