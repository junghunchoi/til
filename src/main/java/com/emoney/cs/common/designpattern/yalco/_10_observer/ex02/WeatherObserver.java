package com.emoney.cs.common.designpattern.yalco._10_observer.ex02;

// Observer interface
interface WeatherObserver {
    void update(float temp, float humidity, float pressure);
}