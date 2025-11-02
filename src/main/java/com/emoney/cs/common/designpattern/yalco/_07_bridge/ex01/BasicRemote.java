package com.emoney.cs.common.designpattern.yalco._07_bridge.ex01;

class BasicRemote extends Remote {
    public BasicRemote(Device device) {
        super(device);
    }

    @Override
    public void power() {
        if (device.isEnabled()) {
            device.turnOff();
        } else {
            device.turnOn();
        }
    }
}