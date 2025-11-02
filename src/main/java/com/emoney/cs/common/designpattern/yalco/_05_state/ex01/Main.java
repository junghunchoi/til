package com.emoney.cs.common.designpattern.yalco._05_state.ex01;

public class Main {
    public static void main(String[] args) {
        Door door = new Door();

        door.open();  // "Door is now Open."
        door.open();  // "Door is already Open."
        door.close(); // "Door is now Closed."
        door.close(); // "Door is already Closed."
    }
}