package com.emoney.cs.common.designpattern.yalco._10_observer.ex02;

// Concrete Observer 3
class ForecastDisplay implements WeatherObserver {
    @Override
    public void update(float temp, float humidity, float pressure) {
        System.out.println(
                "Forecast: " + (pressure < 29.92 ? "Rain" : "Sunny")
        );
    }
}