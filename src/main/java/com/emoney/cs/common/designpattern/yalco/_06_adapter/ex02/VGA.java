package com.emoney.cs.common.designpattern.yalco._06_adapter.ex02;

class VGA {
    void connectWithVgaCable(boolean highQuality) {
        System.out.println(
                "Displaying via VGA with high quality: " + highQuality
        );
    }
}