package com.emoney.cs.common.designpattern.yalco._08_factory_method.ex01;

// Creator abstract class
abstract class VehicleFactory {
    // Factory method
    abstract Vehicle createVehicle();

    // Operations using the factory method
    public void deliverVehicle() {
        Vehicle vehicle = createVehicle();
        System.out.println("Delivering the vehicle:");
        vehicle.drive();
    }
}