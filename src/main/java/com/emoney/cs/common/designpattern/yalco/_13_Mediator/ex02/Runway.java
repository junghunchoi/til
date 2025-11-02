package com.emoney.cs.common.designpattern.yalco._13_Mediator.ex02;

class Runway {
    private AirportMediator mediator;

    public Runway(AirportMediator mediator) {
        this.mediator = mediator;
    }

    public void clear() {
        System.out.println("Runway is clear.");
        mediator.setRunwayAvailability(true);
    }
}
