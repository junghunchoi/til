package com.emoney.cs.common.designpattern.yalco._12_factory_method.ex02;

class PostgreSQLConnection implements Connection {
    public void open() {
        System.out.println("Opening PostgreSQL connection");
    }
    public void close() {
        System.out.println("Closing PostgreSQL connection");
    }
}