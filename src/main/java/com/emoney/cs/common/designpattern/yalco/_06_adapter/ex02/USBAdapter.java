package com.emoney.cs.common.designpattern.yalco._06_adapter.ex02;

class USBAdapter implements DisplayAdapter {
    private USB usb;
    private String data;

    public USBAdapter(USB usb, String data) {
        this.usb = usb;
        this.data = data;
    }

    @Override
    public void display() {
        usb.connectWithUsbCable(data);
    }
}