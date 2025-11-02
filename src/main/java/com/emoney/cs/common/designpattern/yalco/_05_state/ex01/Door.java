package com.emoney.cs.common.designpattern.yalco._05_state.ex01;

public class Door {
    private State state;

    public Door() {
        // Set initial state Closed
        this.state = new ClosedState();
    }

    public void setState(State state) {
        this.state = state;
    }

    public void open() {
        state.open(this);
    }

    public void close() {
        state.close(this);
    }
}