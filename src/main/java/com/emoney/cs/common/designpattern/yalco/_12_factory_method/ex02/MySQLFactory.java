package com.emoney.cs.common.designpattern.yalco._12_factory_method.ex02;

class MySQLFactory implements DatabaseFactory {
    public Connection createConnection() {
        return new MySQLConnection();
    }
    public Command createCommand() {
        return new MySQLCommand();
    }
    public ResultSet createResultSet() {
        return new MySQLResultSet();
    }
}