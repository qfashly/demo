package com.chenxy.demo.sql.model;

/**
 * 比较操作符
 */
public enum ComparisonOperator {
    EQ("="),
    NE("!="),
    GT(">"),
    GE(">="),
    LT("<"),
    LE("<="),
    LIKE("LIKE"),
    IN("IN");

    private final String symbol;

    ComparisonOperator(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }

    public static ComparisonOperator fromSymbol(String symbol) {
        if (symbol == null) {
            throw new IllegalArgumentException("操作符不能为空");
        }
        String normalized = symbol.trim().toUpperCase();
        for (ComparisonOperator operator : values()) {
            if (operator.symbol.equalsIgnoreCase(symbol.trim()) || operator.name().equals(normalized)) {
                return operator;
            }
        }
        throw new IllegalArgumentException("不支持的操作符: " + symbol);
    }
}
