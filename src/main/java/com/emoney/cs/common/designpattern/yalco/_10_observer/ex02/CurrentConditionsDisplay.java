package com.emoney.cs.common.designpattern.yalco._10_observer.ex02;

// Concrete Observer 1
class CurrentConditionsDisplay implements WeatherObserver {
    @Override
    public void update(float temp, float humidity, float pressure) {
        System.out.println(
                "Current: " + temp + "F, " + humidity + "% humidity"
        );
    }
}
