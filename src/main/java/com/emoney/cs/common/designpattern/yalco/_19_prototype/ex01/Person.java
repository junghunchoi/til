package com.emoney.cs.common.designpattern.yalco._19_prototype.ex01;

class Person implements Prototype {
    private String name;
    private int age;
    private String address;

    public Person(String name, int age, String address) {
        this.name = name;
        this.age = age;
        this.address = address;
    }

    public Person(Person other) {
        this.name = other.name;
        this.age = other.age;
        this.address = other.address;
    }

    @Override
    public Person clone() {
        return new Person(this);
    }

    public void setAddress(String newAddress) {
        this.address = newAddress;
    }

    public void displayInfo() {
        System.out.println(
                "Name: " + name + ", Age: " + age
                        + ", Address: " + address);
    }
}