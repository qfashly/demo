package com.chenxy.demo.sql.validator;

import com.chenxy.demo.sql.model.ComparisonNode;
import com.chenxy.demo.sql.model.ComparisonOperator;
import com.chenxy.demo.sql.model.OperandType;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class EtlMonthExpressionMergerTest {

    private final ColumnConditionMerger merger = new ColumnConditionMerger();

    @Test
    public void mergePreservesSubqueryExpression() {
        ComparisonNode subquery = expressionEq("(select max(etl_month) from table1)");
        ComparisonNode business = new ComparisonNode("t1", "c1", ComparisonOperator.EQ, "'28'");

        List<ComparisonNode> merged = merger.merge(Arrays.asList(subquery, business));

        Assert.assertEquals(2, merged.size());
        ComparisonNode etl = findEtlMonth(merged);
        Assert.assertEquals(OperandType.EXPRESSION, etl.getRightOperandType());
        Assert.assertEquals("(select max(etl_month) from table1)", etl.getRightExpression());
        Assert.assertEquals("t1.etl_month = (select max(etl_month) from table1)", etl.toSqlFragment("t1"));
    }

    @Test
    public void mergeSubqueryAndLiteralEtlMonthKeepsBoth() {
        ComparisonNode subquery = expressionEq("(select max(etl_month) from table1)");
        ComparisonNode literal = new ComparisonNode("t1", "etl_month", ComparisonOperator.EQ, "'2026-05-01'");

        List<ComparisonNode> merged = merger.merge(Arrays.asList(subquery, literal));

        Assert.assertEquals(2, merged.size());
        Assert.assertNotNull(findExpression(merged));
        Assert.assertNotNull(findLiteral(merged));
    }

    private ComparisonNode expressionEq(String sql) {
        ComparisonNode node = new ComparisonNode("t1", "etl_month", ComparisonOperator.EQ, null);
        node.setRightOperandType(OperandType.EXPRESSION);
        node.setRightExpression(sql);
        return node;
    }

    private ComparisonNode findEtlMonth(List<ComparisonNode> merged) {
        for (ComparisonNode node : merged) {
            if ("etl_month".equalsIgnoreCase(node.getColumn())) {
                return node;
            }
        }
        Assert.fail("未找到 etl_month 条件");
        return null;
    }

    private ComparisonNode findExpression(List<ComparisonNode> merged) {
        for (ComparisonNode node : merged) {
            if ("etl_month".equalsIgnoreCase(node.getColumn()) && node.hasDynamicRhs()) {
                return node;
            }
        }
        return null;
    }

    private ComparisonNode findLiteral(List<ComparisonNode> merged) {
        for (ComparisonNode node : merged) {
            if ("etl_month".equalsIgnoreCase(node.getColumn()) && !node.hasDynamicRhs()) {
                return node;
            }
        }
        return null;
    }
}
