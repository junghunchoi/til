package com.emoney.cs.common.designpattern.yalco._23_publisher_subscriber.ex02;

// Publisher
class MarketingDepartment {
    private EmailDeliveryService emailService;
    private String eventType;

    public MarketingDepartment(EmailDeliveryService emailService, String eventType) {
        this.emailService = emailService;
        this.eventType = eventType;
    }

    public void launchCampaign(String message) {
        System.out.println("Launching campaign: " + message);
        emailService.sendEmails(eventType, message);
    }
}