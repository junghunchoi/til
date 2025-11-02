package com.emoney.cs.common.designpattern.yalco._03_template_method.ex02;

public class Main {
    public static void main(String[] args) {
        DataProcessor csvProcessor = new CSVDataProcessor();
        csvProcessor.process("CSV data");

        System.out.println();

        DataProcessor jsonProcessor = new JSONDataProcessor();
        jsonProcessor.process("XML data");
    }
}