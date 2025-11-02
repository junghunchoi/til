package com.emoney.cs.common.designpattern.yalco._05_state.ex02;

public class VideoPlayer {
    private State state;

    public VideoPlayer() {
        // Set initial state Stopped
        this.state = new StoppedState();
    }

    public void setState(State state) {
        this.state = state;
    }

    public void play() {
        state.play(this);
    }

    public void stop() {
        state.stop(this);
    }
}