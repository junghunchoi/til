package com.emoney.cs.common.designpattern.yalco._07_bridge.ex01;

// Abstraction
abstract class Remote {
    protected Device device;

    protected Remote(Device device) {
        this.device = device;
    }

    public abstract void power();

    public void volumeUp() {
        device.setVolume(device.isEnabled() ? 1 : 0);
    }

    public void volumeDown() {
        device.setVolume(device.isEnabled() ? -1 : 0);
    }
}