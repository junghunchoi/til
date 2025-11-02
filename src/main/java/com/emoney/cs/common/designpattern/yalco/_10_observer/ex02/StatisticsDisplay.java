package com.emoney.cs.common.designpattern.yalco._10_observer.ex02;

// Concrete Observer 2
class StatisticsDisplay implements WeatherObserver {
    @Override
    public void update(float temp, float humidity, float pressure) {
        System.out.println(
                "Avg/Max/Min temp: " + temp + "/" + (temp + 2) + "/" + (temp - 2)
        );
    }
}