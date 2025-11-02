package com.emoney.cs.common.designpattern.yalco._06_adapter.ex02;

class HDMIAdapter implements DisplayAdapter {
    private HDMI hdmi;
    private int resolution;

    public HDMIAdapter(HDMI hdmi, int resolution) {
        this.hdmi = hdmi;
        this.resolution = resolution;
    }

    @Override
    public void display() {
        hdmi.connectWithHdmiCable(resolution);
    }
}