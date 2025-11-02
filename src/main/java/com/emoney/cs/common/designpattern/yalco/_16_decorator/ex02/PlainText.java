package com.emoney.cs.common.designpattern.yalco._16_decorator.ex02;

// Concrete Component
class PlainText implements Text {
    private String content;

    public PlainText(String content) {
        this.content = content;
    }

    @Override
    public String getContent() {
        return content;
    }
}