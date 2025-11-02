package com.emoney.cs.common.designpattern.yalco._23_publisher_subscriber.ex02;

public class Main {
    public static void main(String[] args) {
        EmailDeliveryService emailService = new EmailDeliveryService();

        MarketingDepartment marketing = new MarketingDepartment(emailService, "ProductLaunch");

        Customer customer1 = new IndividualCustomer("Customer 1");
        Customer customer2 = new IndividualCustomer("Customer 2");

        emailService.subscribe("ProductLaunch", customer1);
        emailService.subscribe("ProductLaunch", customer2);

        // Asynchronously sending emails for the campaign
        marketing.launchCampaign("New Product");

        // Adding another customer who subscribed later
        Customer customer3 = new IndividualCustomer("Customer 3");
        emailService.subscribe("ProductLaunch", customer3);

        // Sending more emails
        marketing.launchCampaign("Update");

        // Allowing time for async processing to complete
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        emailService.shutdown();  // Clean up executor service
    }
}