package com.emoney.cs.common.designpattern.yalco._20_chain_of_responsibility.ex02;

public class Main {
    private static Logger getChainOfLoggers() {
        Logger networkLogger = new NetworkLogger(LogLevel.WARN);
        Logger fileLogger = new FileLogger(LogLevel.DEBUG);
        Logger consoleLogger = new ConsoleLogger(LogLevel.INFO);

        networkLogger.setNextLogger(fileLogger);
        fileLogger.setNextLogger(consoleLogger);

        return networkLogger;
    }

    public static void main(String[] args) {
        Logger loggerChain = getChainOfLoggers();

        loggerChain.logMessage(LogLevel.INFO,
                "This is an information.");

        loggerChain.logMessage(LogLevel.DEBUG,
                "This is a debug level information.");

        loggerChain.logMessage(LogLevel.WARN,
                "This is a warning information.");
    }
}