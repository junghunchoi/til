package com.emoney.cs.common.designpattern.yalco._06_adapter.ex01;

// Concrete Adaptee
class OldMessageSystem implements OldMessageSender {
    @Override
    public int send(String[] messageData) {
        System.out.println("OldMessageSystem: Sending message: " + messageData[0] + " to " + messageData[1]);
        return 1; // Success code
    }
}