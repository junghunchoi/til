package com.emoney.cs.common.designpattern.yalco._13_Mediator.ex01;

public class Main {
    public static void main(String[] args) {
        ChatMediator mediator = new ChatMediatorImpl();

        User user1 = new UserImpl(mediator, "John");
        User user2 = new UserImpl(mediator, "Jane");
        User user3 = new UserImpl(mediator, "Bob");
        User user4 = new UserImpl(mediator, "Alice");

        mediator.addUser(user1);
        mediator.addUser(user2);
        mediator.addUser(user3);
        mediator.addUser(user4);

        user1.send("Hi All");
    }
}