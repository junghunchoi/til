package com.emoney.cs.common.designpattern.yalco._18_memento.ex02;

// Originator
class Document {
    private StringBuilder content;

    public Document() {
        this.content = new StringBuilder();
    }

    public void write(String text) {
        content.append(text);
    }

    public String getContent() {
        return content.toString();
    }

    public DocumentMemento save() {
        return new DocumentMemento(content.toString());
    }

    public void restore(DocumentMemento memento) {
        this.content = new StringBuilder(memento.getContent());
    }
}