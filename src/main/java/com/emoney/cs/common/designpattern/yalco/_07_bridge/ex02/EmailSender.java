package com.emoney.cs.common.designpattern.yalco._07_bridge.ex02;

class EmailSender implements MessageSender {
    @Override
    public void sendMessage(String message) {
        System.out.println("Sending email with message: " + message);
    }
}