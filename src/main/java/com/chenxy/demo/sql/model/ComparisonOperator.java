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
    NOT_LIKE("NOT LIKE"),
    IN("IN"),
    NOT_IN("NOT IN"),
    BETWEEN("BETWEEN"),
    IS_NULL("IS NULL"),
    IS_NOT_NULL("IS NOT NULL");

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
        String trimmed = symbol.trim();
        for (ComparisonOperator operator : values()) {
            if (operator.symbol.equalsIgnoreCase(trimmed)) {
                return operator;
            }
        }
        throw new IllegalArgumentException("不支持的操作符: " + symbol);
    }
}
