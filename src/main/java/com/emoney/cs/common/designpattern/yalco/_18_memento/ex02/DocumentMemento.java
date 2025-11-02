package com.emoney.cs.common.designpattern.yalco._18_memento.ex02;

// Memento
class DocumentMemento {
    private final String content;

    public DocumentMemento(String content) {
        this.content = content;
    }

    public String getContent() {
        return content;
    }
}