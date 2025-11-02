package com.emoney.cs.common.designpattern.yalco._06_adapter.ex01;

// Adaptee interface
interface OldMessageSender {
    int send(String[] messageData);
}