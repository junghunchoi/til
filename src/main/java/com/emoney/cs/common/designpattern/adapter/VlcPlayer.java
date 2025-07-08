package com.emoney.cs.common.designpattern.adapter;

public class VlcPlayer implements AdvancedMediaPlayer{
    @Override
    public void playVlc(String fileName) {
        System.out.println("VLC로 " + fileName + " 재생 중");
    }

    @Override
    public void playMp4(String fileName) {

    }
}
