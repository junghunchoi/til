package com.emoney.cs.common.designpattern.yalco._06_adapter.ex01;

// Adapter
class MessageAdapter implements ModernMessageSender {
    private OldMessageSender oldSystem;

    public MessageAdapter(OldMessageSender oldSystem) {
        this.oldSystem = oldSystem;
    }

    @Override
    public void sendMessage(String message, String recipient) {
        String[] messageData = {message, recipient};
        int result = oldSystem.send(messageData);
        if (result != 1) {
            System.out.println("Failed to send message");
        }
    }
}