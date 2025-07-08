package com.emoney.cs.common.designpattern.adapter;

public class MediaAdapter implements MediaPlayer{
    private AdvancedMediaPlayer advancedMediaPlayer;

    public MediaAdapter(String audioType) {
        switch (audioType.toLowerCase()) {
            case "vlc" -> advancedMediaPlayer = new VlcPlayer();
            case "mp4" -> advancedMediaPlayer = new Mp4Player();
            default -> throw new IllegalArgumentException("Unsupported audio type: " + audioType);
        }
    }

    @Override
    public void play(String audioType, String fileName) {
            switch (audioType.toLowerCase()) {
                case "vlc" -> advancedMediaPlayer.playVlc(fileName);
                case "mp4" -> advancedMediaPlayer.playMp4(fileName);
                default -> throw new IllegalArgumentException("Unsupported audio type: " + audioType);
            }
    }
}
