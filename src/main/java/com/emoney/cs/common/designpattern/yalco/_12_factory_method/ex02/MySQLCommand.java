package com.emoney.cs.common.designpattern.yalco._12_factory_method.ex02;

class MySQLCommand implements Command {
    public void execute(String query) {
        System.out.println("Executing MySQL query: " + query);
    }
}
