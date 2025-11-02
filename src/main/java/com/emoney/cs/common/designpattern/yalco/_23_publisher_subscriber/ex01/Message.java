package com.emoney.cs.common.designpattern.yalco._23_publisher_subscriber.ex01;

// Message class to represent the data being published
class Message {
    private String content;
    private String topic;

    public Message(String content, String topic) {
        this.content = content;
        this.topic = topic;
    }

    public String getContent() {
        return content;
    }

    public String getTopic() {
        return topic;
    }
}
