package com.emoney.cs.common.designpattern.yalco._12_factory_method.ex02;

// Client code
class DatabaseClient {
    private Connection connection;
    private Command command;
    private ResultSet resultSet;

    public DatabaseClient(DatabaseFactory factory) {
        connection = factory.createConnection();
        command = factory.createCommand();
        resultSet = factory.createResultSet();
    }

    public void performDatabaseOperations() {
        connection.open();
        command.execute("SELECT * FROM users");
        resultSet.getResults();
        connection.close();
    }
}