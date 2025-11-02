package com.emoney.cs.common.designpattern.yalco._08_factory_method.ex01;

class MotorcycleFactory extends VehicleFactory {
    @Override
    Vehicle createVehicle() {
        return new Motorcycle();
    }
}