package com.emoney.cs.common.designpattern.yalco._07_bridge.ex02;

public class Main {
    public static void main(String[] args) {
        MessageSender emailSender = new EmailSender();
        MessageSender smsSender = new SMSSender();

        Message textMessage = new TextMessage(emailSender);
        textMessage.send("Hello World!");

        Message encryptedMessage = new EncryptedMessage(smsSender);
        encryptedMessage.send("Hello World!");
    }
}