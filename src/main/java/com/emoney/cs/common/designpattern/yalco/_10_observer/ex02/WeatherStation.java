package com.emoney.cs.common.designpattern.yalco._10_observer.ex02;

// Subject interface
interface WeatherStation {
    void registerObserver(WeatherObserver o);
    void removeObserver(WeatherObserver o);
    void notifyObservers();
}