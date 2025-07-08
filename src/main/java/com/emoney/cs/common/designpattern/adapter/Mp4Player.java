package com.emoney.cs.common.designpattern.adapter;

public class Mp4Player implements AdvancedMediaPlayer{
    @Override
    public void playVlc(String fileName) {

    }

    @Override
    public void playMp4(String fileName) {
        System.out.println("MP4 플레이어로 " + fileName + " 재생 중");
    }
}
