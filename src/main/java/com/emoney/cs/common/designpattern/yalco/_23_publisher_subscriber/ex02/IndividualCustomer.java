package com.emoney.cs.common.designpattern.yalco._23_publisher_subscriber.ex02;

// Concrete Subscriber
class IndividualCustomer implements Customer {
    private String name;

    public IndividualCustomer(String name) {
        this.name = name;
    }

    @Override
    public void receiveEmail(String message) {
        System.out.println(name + " is receiving email async: " + message);
        try {
            Thread.sleep(5000);  // Simulating email reading time
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.println(name + " finished reading email: " + message);
    }
}