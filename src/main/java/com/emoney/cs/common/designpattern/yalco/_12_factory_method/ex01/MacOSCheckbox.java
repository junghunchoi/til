package com.emoney.cs.common.designpattern.yalco._12_factory_method.ex01;

class MacOSCheckbox implements Checkbox {
    @Override
    public void paint() {
        System.out.println("Rendering a checkbox in MacOS style");
    }
}