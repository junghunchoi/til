package com.emoney.cs.common.designpattern.yalco._03_template_method.ex02;

class JSONDataProcessor extends DataProcessor {
    @Override
    protected void loadData(String data) {
        System.out.println("Loading data from JSON file: " + data);
    }

    @Override
    protected boolean isValidData(String data) {
        return data != null && data.contains("JSON");
    }

    @Override
    protected void processData(String data) {
        System.out.println("Processing JSON data");
    }

    @Override
    protected void saveData(String data) {
        System.out.println("Saving JSON data to database");
    }
}