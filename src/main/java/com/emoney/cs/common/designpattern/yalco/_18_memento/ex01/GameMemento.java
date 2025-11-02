package com.emoney.cs.common.designpattern.yalco._18_memento.ex01;

// Memento
class GameMemento {
    private String level;
    private int score;

    public GameMemento(String level, int score) {
        this.level = level;
        this.score = score;
    }

    public String getLevel() {
        return level;
    }

    public int getScore() {
        return score;
    }
}