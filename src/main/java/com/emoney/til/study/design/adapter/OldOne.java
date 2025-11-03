package com.emoney.til.study.design.adapter;

public class OldOne implements OldFunction {
    @Override
    public int send(String text) {
        System.out.println("OldOne: Sending text: " + text);
        return 1; // Success code
    }
}
