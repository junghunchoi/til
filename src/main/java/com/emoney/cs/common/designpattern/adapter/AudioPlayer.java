package com.emoney.cs.common.designpattern.adapter;

import org.springframework.http.converter.json.GsonBuilderUtils;

public class AudioPlayer implements MediaPlayer{
    private MediaPlayer mediaPlayer;

    @Override
    public void play(String audioType, String fileName) {
        if ("mp3".equalsIgnoreCase(audioType)) {
            System.out.println("MP3 플레이어로 " + fileName + " 재생 중");
        } else if ("vlc".equalsIgnoreCase(audioType) || "mp4".equalsIgnoreCase(audioType)) {
            mediaPlayer = new MediaAdapter(audioType);
            mediaPlayer.play(audioType, fileName);
        } else {
            System.out.println("지원하지 않는 오디오 타입: " + audioType);
        }
    }

    public static void main(String[] args) {
        AudioPlayer audioPlayer = new AudioPlayer();

        audioPlayer.play("mp3", "song.mp3");
        audioPlayer.play("vlc", "movie.vlc");
        audioPlayer.play("mp4", "video.mp4");
        audioPlayer.play("avi", "clip.avi"); // 지원하지 않는 타입
    }
}
