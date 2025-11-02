package com.emoney.cs.common.designpattern.yalco._23_publisher_subscriber.ex02;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Broker (Asynchronous)
class EmailDeliveryService {
    private Map<String, List<Customer>> customerGroups = new HashMap<>();
    private ExecutorService executor = Executors.newCachedThreadPool();

    public void subscribe(String eventType, Customer customer) {
        customerGroups.computeIfAbsent(eventType, k -> new ArrayList<>()).add(customer);
    }

    public void sendEmails(String eventType, String message) {
        List<Customer> customers = customerGroups.get(eventType);
        if (customers != null) {
            for (Customer customer : customers) {
                executor.submit(() -> customer.receiveEmail(message));  // Asynchronous email sending
            }
        }
    }

    public void shutdown() {
        executor.shutdown();
    }
}