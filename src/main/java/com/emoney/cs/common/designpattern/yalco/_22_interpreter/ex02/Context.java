package com.emoney.cs.common.designpattern.yalco._22_interpreter.ex02;

import java.util.*;

// Context
class Context {
    private Map<String, List<Map<String, String>>> tables;

    public Context() {
        this.tables = new HashMap<>();
        // Initialize sample data
        List<Map<String, String>> users = new ArrayList<>();
        users.add(new HashMap<String, String>() {{
            put("id", "1");
            put("name", "John");
            put("age", "30");
        }});
        users.add(new HashMap<String, String>() {{
            put("id", "2");
            put("name", "Jane");
            put("age", "25");
        }});
        tables.put("users", users);
    }

    public List<Map<String, String>> getTable(String name) {
        return tables.get(name);
    }

    public void setTable(String name, List<Map<String, String>> table) {
        tables.put(name, table);
    }
}