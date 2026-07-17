package com.chenxy.demo.sql.model;

/**
 * 单个比较表达式，如 t1.c1 = '28'、t1.c1 = t2.c2、t1.c1 IS NULL
 */
public class ComparisonNode {

    /** 左字段表别名，表达式左操作数时可为空 */
    private String tableAlias;
    /** 左字段列名，表达式左操作数时可为空 */
    private String column;
    /** 左操作数为表达式时的 SQL 片段，如 DATE(t1.etl_month) */
    private String leftExpression;
    private ComparisonOperator operator;
    private OperandType rightOperandType = OperandType.LITERAL;
    private String value;
    private String rightTableAlias;
    private String rightColumn;
    private String rightExpression;
    /** BETWEEN 上界 */
    private String betweenUpper;
    private boolean inValues;

    public ComparisonNode() {
    }

    public ComparisonNode(String tableAlias, String column, ComparisonOperator operator, String value) {
        this.tableAlias = tableAlias;
        this.column = column;
        this.operator = operator;
        this.value = value;
    }

    public boolean isCrossTable() {
        return rightOperandType == OperandType.FIELD;
    }

    public boolean isExpressionPredicate() {
        return leftExpression != null && !leftExpression.isEmpty();
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

    public String getLeftExpression() {
        return leftExpression;
    }

    public void setLeftExpression(String leftExpression) {
        this.leftExpression = leftExpression;
    }

    public ComparisonOperator getOperator() {
        return operator;
    }

    public void setOperator(ComparisonOperator operator) {
        this.operator = operator;
    }

    public OperandType getRightOperandType() {
        return rightOperandType;
    }

    public void setRightOperandType(OperandType rightOperandType) {
        this.rightOperandType = rightOperandType;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getRightTableAlias() {
        return rightTableAlias;
    }

    public void setRightTableAlias(String rightTableAlias) {
        this.rightTableAlias = rightTableAlias;
    }

    public String getRightColumn() {
        return rightColumn;
    }

    public void setRightColumn(String rightColumn) {
        this.rightColumn = rightColumn;
    }

    public String getRightExpression() {
        return rightExpression;
    }

    public void setRightExpression(String rightExpression) {
        this.rightExpression = rightExpression;
    }

    public String getBetweenUpper() {
        return betweenUpper;
    }

    public void setBetweenUpper(String betweenUpper) {
        this.betweenUpper = betweenUpper;
    }

    public boolean isInValues() {
        return inValues;
    }

    public void setInValues(boolean inValues) {
        this.inValues = inValues;
    }

    public String getInOperand() {
        return getRhsSql();
    }

    /** 右操作数 SQL：字面量取 value，表达式/子查询取 rightExpression */
    public String getRhsSql() {
        if (rightOperandType == OperandType.EXPRESSION && rightExpression != null && !rightExpression.isEmpty()) {
            return rightExpression;
        }
        return value;
    }

    public boolean hasDynamicRhs() {
        return rightOperandType == OperandType.EXPRESSION;
    }

    public String leftSql(String alias) {
        if (leftExpression != null && !leftExpression.isEmpty()) {
            return leftExpression;
        }
        return alias + "." + column;
    }

    public String toSqlFragment(String alias) {
        String left = leftSql(alias);
        if (operator == ComparisonOperator.IS_NULL || operator == ComparisonOperator.IS_NOT_NULL) {
            return left + " " + operator.getSymbol();
        }
        if (operator == ComparisonOperator.BETWEEN) {
            return left + " BETWEEN " + value + " AND " + betweenUpper;
        }
        if (operator == ComparisonOperator.IN || operator == ComparisonOperator.NOT_IN || inValues) {
            return left + " " + operator.getSymbol() + " " + formatInParentheses(getInOperand());
        }
        if (operator == ComparisonOperator.LIKE || operator == ComparisonOperator.NOT_LIKE) {
            return left + " " + operator.getSymbol() + " " + value;
        }
        if (rightOperandType == OperandType.EXPRESSION) {
            return left + " " + operator.getSymbol() + " " + rightExpression;
        }
        return left + " " + operator.getSymbol() + " " + value;
    }

    public String toCrossTableSqlFragment(String leftJoinAlias, String rightJoinAlias) {
        String left = leftExpression != null && !leftExpression.isEmpty()
                ? leftExpression
                : leftJoinAlias + "." + column;
        String right = rightJoinAlias + "." + rightColumn;
        return left + " " + operator.getSymbol() + " " + right;
    }

    public ComparisonNode copy() {
        ComparisonNode copy = new ComparisonNode(tableAlias, column, operator, value);
        copy.setLeftExpression(leftExpression);
        copy.setRightOperandType(rightOperandType);
        copy.setRightTableAlias(rightTableAlias);
        copy.setRightColumn(rightColumn);
        copy.setRightExpression(rightExpression);
        copy.setBetweenUpper(betweenUpper);
        copy.setInValues(inValues);
        return copy;
    }

    private String formatInParentheses(String operand) {
        if (operand == null || operand.trim().isEmpty()) {
            return "()";
        }
        String trimmed = operand.trim();
        if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
            return trimmed;
        }
        return "(" + trimmed + ")";
    }
}
