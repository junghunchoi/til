package com.emoney.cs.common.designpattern.yalco._07_bridge.ex02;

class TextMessage extends Message {
    public TextMessage(MessageSender messageSender) {
        super(messageSender);
    }

    @Override
    public void send(String message) {
        messageSender.sendMessage("Text Message: " + message);
    }
}