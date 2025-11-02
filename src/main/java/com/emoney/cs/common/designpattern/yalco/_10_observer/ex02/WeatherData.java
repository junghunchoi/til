package com.emoney.cs.common.designpattern.yalco._10_observer.ex02;

import java.util.ArrayList;
import java.util.List;

// Concrete Subject
class WeatherData implements WeatherStation {
    private List<WeatherObserver> observers = new ArrayList<>();
    private float temperature, humidity, pressure;

    public void setMeasurements(
            float temperature, float humidity, float pressure
    ) {
        this.temperature = temperature;
        this.humidity = humidity;
        this.pressure = pressure;
        notifyObservers();
    }

    @Override
    public void registerObserver(WeatherObserver o) {
        observers.add(o);
    }

    @Override
    public void removeObserver(WeatherObserver o) {
        observers.remove(o);
    }

    @Override
    public void notifyObservers() {
        for (WeatherObserver observer : observers) {
            observer.update(temperature, humidity, pressure);
        }
    }
}