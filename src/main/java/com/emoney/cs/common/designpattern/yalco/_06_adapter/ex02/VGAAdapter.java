package com.emoney.cs.common.designpattern.yalco._06_adapter.ex02;

class VGAAdapter implements DisplayAdapter {
    private VGA vga;
    private boolean highQuality;

    public VGAAdapter(VGA vga, boolean highQuality) {
        this.vga = vga;
        this.highQuality = highQuality;
    }

    @Override
    public void display() {
        vga.connectWithVgaCable(highQuality);
    }
}