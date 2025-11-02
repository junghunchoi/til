package com.emoney.cs.common.designpattern.yalco._23_publisher_subscriber.ex01;

public class Main {
    public static void main(String[] args) {
        Broker broker = new Broker();

        NewsPublisher publisher = new NewsPublisher(broker);

        NewsSubscriber subscriber1 = new NewsSubscriber("Subscriber 1");
        NewsSubscriber subscriber2 = new NewsSubscriber("Subscriber 2");

        broker.subscribe("sports", subscriber1);
        broker.subscribe("weather", subscriber2);
        broker.subscribe("sports", subscriber2);

        publisher.publish(new Message("Liverpool won the match", "sports"));
        publisher.publish(new Message("It's sunny today", "weather"));
    }
}