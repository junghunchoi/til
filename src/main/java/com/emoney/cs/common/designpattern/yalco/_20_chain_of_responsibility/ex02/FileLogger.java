package com.emoney.cs.common.designpattern.yalco._20_chain_of_responsibility.ex02;

class FileLogger extends Logger {
    public FileLogger(LogLevel level) {
        this.level = level;
    }

    @Override
    protected void write(String message) {
        System.out.println("File::Logger: " + message);
    }
}