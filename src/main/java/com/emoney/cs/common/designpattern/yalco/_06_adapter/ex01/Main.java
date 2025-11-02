package com.emoney.cs.common.designpattern.yalco._06_adapter.ex01;

// Client
class Main {
    public static void main(String[] args) {
        OldMessageSender oldSystem = new OldMessageSystem();
        ModernMessageSender adapter = new MessageAdapter(oldSystem);

        adapter.sendMessage("Hello, World!", "john@example.com");
    }
}