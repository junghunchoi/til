package com.emoney.cs.common.designpattern.yalco._07_bridge.ex01;

public class Main {
    public static void main(String[] args) {
        Device tv = new TV();
        Remote basicRemote = new BasicRemote(tv);
        basicRemote.power();
        basicRemote.volumeUp();

        System.out.println();

        Device radio = new Radio();
        AdvancedRemote advancedRemote = new AdvancedRemote(radio);
        advancedRemote.power();
        advancedRemote.mute();
    }
}