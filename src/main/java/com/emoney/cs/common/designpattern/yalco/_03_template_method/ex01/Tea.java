package com.emoney.cs.common.designpattern.yalco._03_template_method.ex01;

class Tea extends Beverage {
    void brew() {
        System.out.println("Steeping the tea");
    }

    void addCondiments() {
        System.out.println("Adding lemon");
    }
}