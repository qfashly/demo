package com.chenxy.demo.sql.model;

/**
 * 单个比较表达式，如 t1.c1 = '28'
 */
public class ComparisonNode {

    private String tableAlias;
    private String column;
    private ComparisonOperator operator;
    private String value;
    private boolean inValues;

    public ComparisonNode() {
    }

    public ComparisonNode(String tableAlias, String column, ComparisonOperator operator, String value) {
        this.tableAlias = tableAlias;
        this.column = column;
        this.operator = operator;
        this.value = value;
    }

    public String getTableAlias() {
        return tableAlias;
    }

    public void setTableAlias(String tableAlias) {
        this.tableAlias = tableAlias;
    }

    public String getColumn() {
        return column;
    }

    public void setColumn(String column) {
        this.column = column;
    }

    public ComparisonOperator getOperator() {
        return operator;
    }

    public void setOperator(ComparisonOperator operator) {
        this.operator = operator;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public boolean isInValues() {
        return inValues;
    }

    public void setInValues(boolean inValues) {
        this.inValues = inValues;
    }

    public String toSqlFragment(String alias) {
        String field = alias + "." + column;
        if (operator == ComparisonOperator.IN || inValues) {
            return field + " IN (" + value + ")";
        }
        if (operator == ComparisonOperator.LIKE) {
            return field + " LIKE " + value;
        }
        return field + " " + operator.getSymbol() + " " + value;
    }

    public ComparisonNode copy() {
        ComparisonNode copy = new ComparisonNode(tableAlias, column, operator, value);
        copy.setInValues(inValues);
        return copy;
    }
}
