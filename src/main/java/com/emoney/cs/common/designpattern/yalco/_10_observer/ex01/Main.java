package com.emoney.cs.common.designpattern.yalco._10_observer.ex01;

public class Main {
    public static void main(String[] args) {
        NewsAgency agency = new NewsAgency();

        NewsChannel channel1 = new NewsChannel("Channel 1");
        NewsChannel channel2 = new NewsChannel("Channel 2");

        agency.registerObserver(channel1);
        agency.registerObserver(channel2);

        agency.setNews("Breaking news: Observer Pattern in action!");

        agency.removeObserver(channel2);

        agency.setNews("Another update: Channel 2 unsubscribed.");
    }
}