package com.emoney.cs.common.designpattern.yalco._19_prototype.ex02;

import java.util.HashMap;
import java.util.Map;

// Concrete document class
class TextDocument implements Document {
    private String content;

    public TextDocument(String content) {
        this.content = content;
    }

    @Override
    public Document clone() {
        return new TextDocument(this.content);
    }

    @Override
    public void setContent(String content) {
        this.content = content;
    }

    @Override
    public String getContent() {
        return content;
    }
}