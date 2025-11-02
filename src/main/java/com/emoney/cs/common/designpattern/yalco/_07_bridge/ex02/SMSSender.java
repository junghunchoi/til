package com.emoney.cs.common.designpattern.yalco._07_bridge.ex02;

class SMSSender implements MessageSender {
    @Override
    public void sendMessage(String message) {
        System.out.println("Sending SMS with message: " + message);
    }
}