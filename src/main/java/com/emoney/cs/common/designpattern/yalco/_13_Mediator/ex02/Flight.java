package com.emoney.cs.common.designpattern.yalco._13_Mediator.ex02;

class Flight {
    private AirportMediator mediator;
    private String flightNumber;

    public Flight(AirportMediator mediator, String flightNumber) {
        this.mediator = mediator;
        this.flightNumber = flightNumber;
    }

    public void land() {
        if (mediator.isRunwayAvailable()) {
            System.out.println("Flight " + flightNumber + " is landing.");
            mediator.setRunwayAvailability(false);
        } else {
            System.out.println("Flight " + flightNumber + " is waiting to land.");
        }
    }
}