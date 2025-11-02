package com.emoney.cs.common.designpattern.yalco._12_factory_method.ex01;

class WindowsButton implements Button {
    @Override
    public void paint() {
        System.out.println("Rendering a button in Windows style");
    }
}