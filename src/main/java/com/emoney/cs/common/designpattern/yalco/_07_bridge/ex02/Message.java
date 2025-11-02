package com.emoney.cs.common.designpattern.yalco._07_bridge.ex02;

abstract class Message {
    protected MessageSender messageSender;

    protected Message(MessageSender messageSender) {
        this.messageSender = messageSender;
    }

    public abstract void send(String message);
}