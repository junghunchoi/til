package com.emoney.cs.common.designpattern.yalco._18_memento.ex01;

// Client
public class Main {
    public static void main(String[] args) {
        Game game = new Game();
        GameCaretaker caretaker = new GameCaretaker();

        game.set("Level 1", 100);
        caretaker.add(game.save());

        game.set("Level 2", 200);
        caretaker.add(game.save());

        game.set("Level 3", 300);

        game.restore(caretaker.get(1)); // Restores to Level 2, Score 200
        game.restore(caretaker.get(0)); // Restores to Level 1, Score 100
    }
}