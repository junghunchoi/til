package com.emoney.cs.common.designpattern.yalco._06_adapter.ex02;

import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        USB usb = new USB();
        HDMI hdmi = new HDMI();
        VGA vga = new VGA();

        List<DisplayAdapter> adapters = new ArrayList<>();
        adapters.add(new USBAdapter(usb, "Video Data"));
        adapters.add(new HDMIAdapter(hdmi, 1080));
        adapters.add(new VGAAdapter(vga, true));

        for (DisplayAdapter adapter : adapters) {
            adapter.display();
        }
    }
}