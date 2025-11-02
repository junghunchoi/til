package com.emoney.cs.common.designpattern.yalco._22_interpreter.ex02;

import java.util.*;

// SQL Parser
class SQLParser {
    public static Expression parse(String query) {
        String[] parts = query.split("\\s+");
        if (!parts[0].equalsIgnoreCase("SELECT")) {
            throw new RuntimeException("Only SELECT statements are supported");
        }

        List<String> columns = Arrays.asList(parts[1].split(","));
        String tableName = parts[3];

        Expression whereClause = null;
        if (parts.length > 4 && parts[4].equalsIgnoreCase("WHERE")) {
            whereClause = new WhereExpression(tableName, parts[5], parts[6], parts[7]);
        }

        return new SelectExpression(tableName, columns, whereClause);
    }
}