package com.emoney.cs.common.designpattern.yalco._12_factory_method.ex02;

// Usage
public class Main {
    public static void main(String[] args) {
        DatabaseClient mysqlClient
                = new DatabaseClient(new MySQLFactory());
        mysqlClient.performDatabaseOperations();

        System.out.println("\nSwitching to PostgreSQL...\n");

        DatabaseClient postgresClient
                = new DatabaseClient(new PostgreSQLFactory());
        postgresClient.performDatabaseOperations();
    }
}