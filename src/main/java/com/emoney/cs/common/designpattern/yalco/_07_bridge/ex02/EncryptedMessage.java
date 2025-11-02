package com.emoney.cs.common.designpattern.yalco._07_bridge.ex02;

class EncryptedMessage extends Message {
    public EncryptedMessage(MessageSender messageSender) {
        super(messageSender);
    }

    @Override
    public void send(String message) {
        String encryptedMessage = encrypt(message);
        messageSender.sendMessage("Encrypted Message: " + encryptedMessage);
    }

    private String encrypt(String message) {
        return new StringBuilder(message).reverse().toString();
    }
}