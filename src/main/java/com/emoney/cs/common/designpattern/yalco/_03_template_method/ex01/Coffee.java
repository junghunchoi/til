package com.emoney.cs.common.designpattern.yalco._03_template_method.ex01;


class Coffee extends Beverage {
    void brew() {
        System.out.println("Dripping coffee through filter");
    }

    void addCondiments() {
        System.out.println("Adding sugar and milk");
    }
}