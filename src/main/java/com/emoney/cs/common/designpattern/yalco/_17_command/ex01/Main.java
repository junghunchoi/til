package com.emoney.cs.common.designpattern.yalco._17_command.ex01;

public class Main {
    public static void main(String[] args) {
        Light livingRoomLight = new Light();

        Command lightOn = new LightOnCommand(livingRoomLight);
        Command lightOff = new LightOffCommand(livingRoomLight);

        RemoteControl remote = new RemoteControl();

        remote.setCommand(lightOn);
        remote.pressButton(); // "Light is ON"

        remote.setCommand(lightOff);
        remote.pressButton(); // "Light is OFF"
    }
}