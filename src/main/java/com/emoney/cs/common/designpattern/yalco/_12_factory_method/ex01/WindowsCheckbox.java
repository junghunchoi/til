package com.emoney.cs.common.designpattern.yalco._12_factory_method.ex01;

class WindowsCheckbox implements Checkbox {
    @Override
    public void paint() {
        System.out.println("Rendering a checkbox in Windows style");
    }
}