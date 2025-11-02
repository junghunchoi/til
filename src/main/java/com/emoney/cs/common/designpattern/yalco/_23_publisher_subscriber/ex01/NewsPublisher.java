package com.emoney.cs.common.designpattern.yalco._23_publisher_subscriber.ex01;

// Concrete Publisher
class NewsPublisher implements Publisher {
    private Broker broker;

    public NewsPublisher(Broker broker) {
        this.broker = broker;
    }

    @Override
    public void publish(Message message) {
        System.out.println("Publishing: " + message.getContent() + " on topic: " + message.getTopic());
        broker.publish(message);
    }
}
