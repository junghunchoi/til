package com.emoney.cs.common.designpattern.yalco._07_bridge.ex01;

class Radio implements Device {
    private boolean on = false;
    private int volume = 30;

    @Override
    public void turnOn() {
        on = true;
        System.out.println("Radio is now ON.");
    }

    @Override
    public void turnOff() {
        on = false;
        System.out.println("Radio is now OFF.");
    }

    @Override
    public void setVolume(int volume) {
        this.volume = volume;
        System.out.println("Radio volume set to " + volume);
    }

    @Override
    public boolean isEnabled() {
        return on;
    }
}