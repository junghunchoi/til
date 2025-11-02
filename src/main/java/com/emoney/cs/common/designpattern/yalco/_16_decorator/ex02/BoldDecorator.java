package com.emoney.cs.common.designpattern.yalco._16_decorator.ex02;

class BoldDecorator extends TextDecorator {
    public BoldDecorator(Text text) {
        super(text);
    }

    @Override
    public String getContent() {
        return "<b>" + super.getContent() + "</b>";
    }
}
