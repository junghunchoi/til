package com.emoney.cs.common.designpattern.yalco._05_state.ex01;

public interface State {
    void open(Door door);
    void close(Door door);
}