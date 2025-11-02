package com.emoney.cs.common.designpattern.yalco._01_facade.ex01;

public class SmartHomeFacade {
    private Thermostat thermostat;
    private Lights lights;
    private CoffeeMaker coffeeMaker;

    public SmartHomeFacade(
            Thermostat thermostat, Lights lights, CoffeeMaker coffeeMaker
    ) {
        this.thermostat = thermostat;
        this.lights = lights;
        this.coffeeMaker = coffeeMaker;
    }

    public void wakeUp() {
        System.out.println("Waking up...");
        thermostat.setTemperature(22);
        lights.on();
        coffeeMaker.brewCoffee();
    }

    public void leaveHome() {
        System.out.println("Leaving home...");
        thermostat.setTemperature(18);
        lights.off();
    }
}