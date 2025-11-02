package com.emoney.cs.common.designpattern.yalco._13_Mediator.ex01;

import java.util.List;
import java.util.ArrayList;


// Concrete implementation of the Mediator interface
public class ChatMediatorImpl implements ChatMediator {
    private List<User> users;

    public ChatMediatorImpl() {
        this.users = new ArrayList<>();
    }

    @Override
    public void addUser(User user) {
        this.users.add(user);
    }

    @Override
    public void sendMessage(String message, User user) {
        for (User u : this.users) {
            if (u != user) {
                u.receive(message);
            }
        }
    }
}