package com.chenxy.demo.sql.validator;

import com.chenxy.demo.sql.model.ComparisonNode;
import com.chenxy.demo.sql.model.ComparisonOperator;
import com.chenxy.demo.sql.model.ConditionNode;

import java.util.ArrayList;
import java.util.List;
/**
 * 处理同表多个最小条件单元：仅当 etl_month 约束完全一致时合并，否则保持各自独立快照
 */
public final class SameTableMinUnitProcessor {
    private SameTableMinUnitProcessor() {
    }
    public static List<ConditionNode> process(List<ConditionNode> units, ColumnConditionMerger merger) {
        if (units == null || units.size() <= 1) {
            return units;
        }
        if (SameTableEtlMonthContradictionChecker.isContradictory(units, merger)) {
            return units;
        }
        if (hasIdenticalEtlMonthConstraint(units)) {
            return mergeIntoSingleUnit(units, merger);
        }
        return units;
    }
    private static boolean hasIdenticalEtlMonthConstraint(List<ConditionNode> units) {
        String signature = null;
        for (ConditionNode unit : units) {
            String current = etlMonthSignature(unit);
            if (current == null) {
                return false;
            }
            if (signature == null) {
                signature = current;
            } else if (!signature.equals(current)) {
                return false;
            }
        }
        return true;
    }
    private static String etlMonthSignature(ConditionNode unit) {
        for (ComparisonNode comparison : unit.getComparisons()) {
            if ("etl_month".equalsIgnoreCase(comparison.getColumn())) {
                return comparison.getOperator().name() + "#" + normalizeValue(comparison);
            }
        }
        return null;
    }
    private static String normalizeValue(ComparisonNode comparison) {
        if (comparison.getOperator() == ComparisonOperator.BETWEEN) {
            return comparison.getRhsSql() + "#" + comparison.getBetweenUpper();
        }
        return comparison.getRhsSql();
    }
    private static List<ConditionNode> mergeIntoSingleUnit(List<ConditionNode> units, ColumnConditionMerger merger) {
        List<ComparisonNode> comparisons = new ArrayList<ComparisonNode>();
        for (ConditionNode unit : units) {
            comparisons.addAll(unit.getComparisons());
        }
        List<ComparisonNode> merged = merger.merge(comparisons);
        ConditionNode sample = units.get(0);
        ConditionNode mergedUnit = ConditionNode.minUnit(sample.getTableAlias(), sample.getTableName(), merged);
        List<ConditionNode> result = new ArrayList<ConditionNode>();
        result.add(mergedUnit);
        return result;
    }
}
