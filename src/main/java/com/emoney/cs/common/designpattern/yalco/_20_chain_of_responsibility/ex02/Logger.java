package com.emoney.cs.common.designpattern.yalco._20_chain_of_responsibility.ex02;

abstract class Logger {
    protected LogLevel level;
    protected Logger nextLogger;

    public void setNextLogger(Logger nextLogger) {
        this.nextLogger = nextLogger;
    }

    public void logMessage(LogLevel level, String message) {
        if (this.level.ordinal() <= level.ordinal()) {
            write(message);
        }
        if (nextLogger != null) {
            nextLogger.logMessage(level, message);
        }
    }

    protected abstract void write(String message);
}