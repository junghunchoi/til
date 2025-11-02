package com.emoney.cs.common.designpattern.yalco._16_decorator.ex02;

class UnderlineDecorator extends TextDecorator {
    public UnderlineDecorator(Text text) {
        super(text);
    }

    @Override
    public String getContent() {
        return "<u>" + super.getContent() + "</u>";
    }
}