package com.emoney.cs.common.designpattern.yalco._22_interpreter.ex02;

import java.util.*;

// Client code
public class Main {
    public static void main(String[] args) {
        Context context = new Context();

        // Test query 1: Select all columns from users
        String query1 = "SELECT * FROM users";
        Expression expr1 = SQLParser.parse(query1);
        List<Map<String, String>> result1 = expr1.interpret(context);
        System.out.println("Result of query: " + query1);
        for (Map<String, String> row : result1) {
            System.out.println(row);
        }

        // Test query 2: Select name and age of users older than 27
        String query2 = "SELECT name,age FROM users WHERE age > 27";
        Expression expr2 = SQLParser.parse(query2);
        List<Map<String, String>> result2 = expr2.interpret(context);
        System.out.println("\nResult of query: " + query2);
        for (Map<String, String> row : result2) {
            System.out.println(row);
        }
    }
}
