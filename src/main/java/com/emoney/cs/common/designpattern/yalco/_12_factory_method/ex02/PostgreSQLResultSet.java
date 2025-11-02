package com.emoney.cs.common.designpattern.yalco._12_factory_method.ex02;

class PostgreSQLResultSet implements ResultSet {
    public void getResults() {
        System.out.println("Getting results from PostgreSQL database");
    }
}