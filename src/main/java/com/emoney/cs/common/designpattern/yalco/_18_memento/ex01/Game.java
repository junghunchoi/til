package com.emoney.cs.common.designpattern.yalco._18_memento.ex01;

// Originator
class Game {
    private String level;
    private int score;

    public void set(String level, int score) {
        this.level = level;
        this.score = score;
        System.out.println(
                "Game state set to - Level: " + level + ", Score: " + score
        );
    }

    public GameMemento save() {
        return new GameMemento(level, score);
    }

    public void restore(GameMemento memento) {
        this.level = memento.getLevel();
        this.score = memento.getScore();
        System.out.println(
                "Game state restored to - Level: " + level
                        + ", Score: " + score);
    }
}