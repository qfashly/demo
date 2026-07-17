package com.chenxy.demo.sql.validator;

import com.chenxy.demo.sql.model.ComparisonNode;
import com.chenxy.demo.sql.model.ComparisonOperator;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class ColumnConditionMergerTest {

    private final ColumnConditionMerger merger = new ColumnConditionMerger();

    @Test
    public void mergeDuplicateEtlMonthEq() {
        ComparisonNode a = new ComparisonNode("t3", "etl_month", ComparisonOperator.EQ, "'2026-05-01'");
        ComparisonNode b = new ComparisonNode("t3", "etl_month", ComparisonOperator.EQ, "'2026-05-01'");
        List<ComparisonNode> merged = merger.merge(Arrays.asList(a, b, new ComparisonNode("t3", "c3", ComparisonOperator.EQ, "'28'")));

        Assert.assertEquals(2, merged.size());
        Assert.assertEquals(ComparisonOperator.EQ, merged.get(0).getOperator());
        Assert.assertEquals("etl_month", merged.get(0).getColumn());
        Assert.assertEquals("'2026-05-01'", merged.get(0).getValue());
    }

    @Test
    public void mergeEtlMonthEqToIn() {
        ComparisonNode a = new ComparisonNode("t3", "etl_month", ComparisonOperator.EQ, "'2026-05-01'");
        ComparisonNode b = new ComparisonNode("t3", "etl_month", ComparisonOperator.EQ, "'2026-06-01'");
        List<ComparisonNode> merged = merger.merge(Arrays.asList(a, b));

        Assert.assertEquals(1, merged.size());
        Assert.assertEquals(ComparisonOperator.IN, merged.get(0).getOperator());
        Assert.assertTrue(merged.get(0).getValue().contains("'2026-05-01'"));
        Assert.assertTrue(merged.get(0).getValue().contains("'2026-06-01'"));
    }

    @Test
    public void mergeInvalidEtlMonthRangeKeepsSeparateBounds() {
        ComparisonNode ge = new ComparisonNode("t3", "etl_month", ComparisonOperator.GE, "'2026-05-01'");
        ComparisonNode le = new ComparisonNode("t3", "etl_month", ComparisonOperator.LE, "'2025-06-01'");
        List<ComparisonNode> merged = merger.merge(Arrays.asList(ge, le));

        Assert.assertEquals(2, merged.size());
        Assert.assertEquals(ComparisonOperator.GE, merged.get(0).getOperator());
        Assert.assertEquals(ComparisonOperator.LE, merged.get(1).getOperator());
    }

    @Test
    public void mergeEtlMonthGeLeToBetween() {
        ComparisonNode ge = new ComparisonNode("t3", "etl_month", ComparisonOperator.GE, "'2026-05-01'");
        ComparisonNode le = new ComparisonNode("t3", "etl_month", ComparisonOperator.LE, "'2026-06-01'");
        List<ComparisonNode> merged = merger.merge(Arrays.asList(ge, le));

        Assert.assertEquals(1, merged.size());
        Assert.assertEquals(ComparisonOperator.BETWEEN, merged.get(0).getOperator());
        Assert.assertEquals("'2026-05-01'", merged.get(0).getValue());
        Assert.assertEquals("'2026-06-01'", merged.get(0).getBetweenUpper());
    }
}
