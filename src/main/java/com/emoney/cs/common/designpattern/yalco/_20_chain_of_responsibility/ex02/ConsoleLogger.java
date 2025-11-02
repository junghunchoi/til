package com.emoney.cs.common.designpattern.yalco._20_chain_of_responsibility.ex02;

class ConsoleLogger extends Logger {
    public ConsoleLogger(LogLevel level) {
        this.level = level;
    }

    @Override
    protected void write(String message) {
        System.out.println("Console::Logger: " + message);
    }
}