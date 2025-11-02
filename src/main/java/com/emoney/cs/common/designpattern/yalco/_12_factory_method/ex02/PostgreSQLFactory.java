package com.emoney.cs.common.designpattern.yalco._12_factory_method.ex02;

class PostgreSQLFactory implements DatabaseFactory {
    public Connection createConnection() {
        return new PostgreSQLConnection();
    }
    public Command createCommand() {
        return new PostgreSQLCommand();
    }
    public ResultSet createResultSet() {
        return new PostgreSQLResultSet();
    }
}