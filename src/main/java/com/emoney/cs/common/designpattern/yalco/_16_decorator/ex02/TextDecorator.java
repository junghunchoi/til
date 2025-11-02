package com.emoney.cs.common.designpattern.yalco._16_decorator.ex02;

// Base Decorator
abstract class TextDecorator implements Text {
    protected Text decoratedText;

    public TextDecorator(Text text) {
        this.decoratedText = text;
    }

    @Override
    public String getContent() {
        return decoratedText.getContent();
    }
}