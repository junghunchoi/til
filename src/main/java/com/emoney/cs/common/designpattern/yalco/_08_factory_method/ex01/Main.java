package com.emoney.cs.common.designpattern.yalco._08_factory_method.ex01;

public class Main {
    public static void main(String[] args) {
        VehicleFactory carFactory = new CarFactory();
        carFactory.deliverVehicle();

        VehicleFactory motorcycleFactory = new MotorcycleFactory();
        motorcycleFactory.deliverVehicle();
    }
}