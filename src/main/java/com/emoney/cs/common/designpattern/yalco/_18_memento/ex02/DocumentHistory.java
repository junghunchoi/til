package com.emoney.cs.common.designpattern.yalco._18_memento.ex02;

import java.util.Stack;

// Caretaker
class DocumentHistory {
    private final Stack<DocumentMemento> history = new Stack<>();

    public void push(DocumentMemento memento) {
        history.push(memento);
    }

    public DocumentMemento pop() {
        if (!history.isEmpty()) {
            return history.pop();
        }
        return null;
    }
}