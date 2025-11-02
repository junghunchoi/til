package com.emoney.cs.common.designpattern.yalco._23_publisher_subscriber.ex01;

import java.util.*;

// Broker class to manage subscriptions and message routing
class Broker {
    private Map<String, List<Subscriber>> subscribers = new HashMap<>();

    public void subscribe(String topic, Subscriber subscriber) {
        subscribers.computeIfAbsent(topic, k -> new ArrayList<>()).add(subscriber);
    }

    public void publish(Message message) {
        List<Subscriber> topicSubscribers = subscribers.get(message.getTopic());
        if (topicSubscribers != null) {
            for (Subscriber subscriber : topicSubscribers) {
                subscriber.update(message);
            }
        }
    }
}