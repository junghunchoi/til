package com.emoney.cs.common.designpattern.yalco._12_factory_method.ex02;

class MySQLConnection implements Connection {
    public void open() {
        System.out.println("Opening MySQL connection");
    }
    public void close() {
        System.out.println("Closing MySQL connection");
    }
}