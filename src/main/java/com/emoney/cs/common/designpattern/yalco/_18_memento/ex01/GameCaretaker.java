package com.emoney.cs.common.designpattern.yalco._18_memento.ex01;

import java.util.List;
import java.util.ArrayList;

// Caretaker
class GameCaretaker {
    private List<GameMemento> mementoList = new ArrayList<>();

    public void add(GameMemento state) {
        mementoList.add(state);
    }

    public GameMemento get(int index) {
        return mementoList.get(index);
    }
}