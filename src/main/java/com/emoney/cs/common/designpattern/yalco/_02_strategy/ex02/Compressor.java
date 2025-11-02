package com.emoney.cs.common.designpattern.yalco._02_strategy.ex02;

// Context class
class Compressor {
    private CompressionStrategy strategy;

    public void setCompressionStrategy(CompressionStrategy strategy) {
        this.strategy = strategy;
    }

    public String compress(String data) {
        return strategy.compress(data);
    }
}