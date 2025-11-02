package com.emoney.cs.common.designpattern.yalco._07_bridge.ex01;

// Implementor
interface Device {
    void turnOn();
    void turnOff();
    void setVolume(int volume);
    boolean isEnabled();
}