package com.emoney.cs.common.designpattern.yalco._12_factory_method.ex02;

class PostgreSQLCommand implements Command {
    public void execute(String query) {
        System.out.println("Executing PostgreSQL query: " + query);
    }
}
