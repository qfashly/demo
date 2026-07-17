package com.chenxy.demo.sql.validator;


import com.chenxy.demo.sql.model.ComparisonNode;
import com.chenxy.demo.sql.model.ConditionNode;

import java.util.ArrayList;
import java.util.List;

/**
 * 合并同一表的最小条件单元：仅当 etl_month 可在同一分区快照内同时满足时才合并
 */
public final class MinUnitMergeHelper {
    private static final String ETL_MONTH = "etl_month";
    private MinUnitMergeHelper() {
    }
    public static List<ConditionNode> mergeCompatible(List<ConditionNode> units, ColumnConditionMerger merger) {
        if (units == null || units.size() <= 1) {
            return units;
        }
        List<ConditionNode> pending = new ArrayList<ConditionNode>(units);
        List<ConditionNode> merged = new ArrayList<ConditionNode>();
        while (!pending.isEmpty()) {
            ConditionNode current = pending.remove(0);
            boolean combined = false;
            for (int i = 0; i < merged.size(); i++) {
                ConditionNode candidate = tryMerge(merged.get(i), current, merger);
                if (candidate != null) {
                    merged.set(i, candidate);
                    combined = true;
                    break;
                }
            }
            if (!combined) {
                merged.add(current);
            }
        }
        return merged;
    }
    public static ConditionNode tryMerge(ConditionNode left, ConditionNode right, ColumnConditionMerger merger) {
        if (left == null || right == null) {
            return null;
        }
        if (!left.getTableAlias().equals(right.getTableAlias())) {
            return null;
        }
        List<ComparisonNode> comparisons = new ArrayList<ComparisonNode>(left.getComparisons());
        comparisons.addAll(right.getComparisons());
        List<ComparisonNode> mergedComparisons = merger.merge(comparisons);
        if (isEtlMonthContradictory(mergedComparisons)) {
            return null;
        }
        String tableName = left.getTableName() != null ? left.getTableName() : right.getTableName();
        return ConditionNode.minUnit(left.getTableAlias(), tableName, mergedComparisons);
    }
    public static boolean isEtlMonthContradictory(List<ComparisonNode> comparisons) {
        List<ComparisonNode> etlMonthComparisons = new ArrayList<ComparisonNode>();
        for (ComparisonNode comparison : comparisons) {
            if (ETL_MONTH.equalsIgnoreCase(comparison.getColumn())) {
                etlMonthComparisons.add(comparison);
            }
        }
        if (etlMonthComparisons.size() <= 1) {
            return false;
        }
        return ConditionValidator.areEtlMonthConstraintsContradictory(etlMonthComparisons);
    }
}