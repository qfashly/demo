package com.chenxy.demo.sql.validator;

import com.chenxy.demo.sql.model.ComparisonNode;
import com.chenxy.demo.sql.model.ConditionNode;

import java.util.ArrayList;
import java.util.List;

/**
 * 检测同表多个 MIN_UNIT 的 etl_month 是否无法由单一分区月份同时满足
 */
public final class SameTableEtlMonthContradictionChecker {

    private SameTableEtlMonthContradictionChecker() {
    }

    public static boolean isContradictory(List<ConditionNode> units, ColumnConditionMerger merger) {
        if (units == null || units.size() <= 1) {
            return false;
        }
        List<ComparisonNode> etlMonthComparisons = collectEtlMonthComparisons(units);
        if (etlMonthComparisons.size() <= 1) {
            return false;
        }
        List<ComparisonNode> merged = merger.merge(etlMonthComparisons);
        return ConditionValidator.areEtlMonthConstraintsContradictory(merged);
    }

    private static List<ComparisonNode> collectEtlMonthComparisons(List<ConditionNode> units) {
        List<ComparisonNode> comparisons = new ArrayList<ComparisonNode>();
        for (ConditionNode unit : units) {
            for (ComparisonNode comparison : unit.getComparisons()) {
                if ("etl_month".equalsIgnoreCase(comparison.getColumn())) {
                    comparisons.add(comparison);
                }
            }
        }
        return comparisons;
    }
}
