package com.emoney.cs.common.designpattern.yalco._23_publisher_subscriber.ex01;

// Concrete Subscriber
class NewsSubscriber implements Subscriber {
    private String name;

    public NewsSubscriber(String name) {
        this.name = name;
    }

    @Override
    public void update(Message message) {
        System.out.println(name + " received: " + message.getContent() + " on topic: " + message.getTopic());
    }
}