package com.emoney.cs.common.designpattern.yalco._10_observer.ex02;

// Main class
public class Main {
    public static void main(String[] args) {
        WeatherData weatherData = new WeatherData();

        CurrentConditionsDisplay currentDisplay
                = new CurrentConditionsDisplay();
        StatisticsDisplay statisticsDisplay = new StatisticsDisplay();
        ForecastDisplay forecastDisplay = new ForecastDisplay();

        weatherData.registerObserver(currentDisplay);
        weatherData.registerObserver(statisticsDisplay);
        weatherData.registerObserver(forecastDisplay);

        weatherData.setMeasurements(80, 65, 30.4f);
    }
}