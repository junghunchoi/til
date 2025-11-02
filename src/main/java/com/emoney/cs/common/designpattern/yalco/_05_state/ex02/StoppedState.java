package com.emoney.cs.common.designpattern.yalco._05_state.ex02;

public class StoppedState implements State {
    @Override
    public void play(VideoPlayer player) {
        System.out.println("Starting the video.");
        player.setState(new PlayingState());
    }

    @Override
    public void stop(VideoPlayer player) {
        System.out.println("Video is already stopped.");
    }
}