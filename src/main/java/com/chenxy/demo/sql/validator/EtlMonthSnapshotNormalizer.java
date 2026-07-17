package com.chenxy.demo.sql.validator;


import com.chenxy.demo.sql.model.ComparisonNode;
import com.chenxy.demo.sql.model.ComparisonOperator;
import com.chenxy.demo.sql.model.ConditionNode;

import java.util.ArrayList;
import java.util.List;
/**
 * 同表多分区快照条件归并：首组保留下界、末组保留合并上界、中间组去掉 etl_month
 */
public final class EtlMonthSnapshotNormalizer {
    private static final String ETL_MONTH = "etl_month";
    private EtlMonthSnapshotNormalizer() {
    }
    public static List<ConditionNode> normalize(List<ConditionNode> units) {
        if (units == null || units.isEmpty()) {
            return units;
        }
        if (units.size() == 1) {
            return units;
        }
        String globalMaxUpper = resolveGlobalMaxUpper(units);
        boolean lastNeedsUpper = lastUnitHasUpperType(units.get(units.size() - 1));
        int lastIndex = units.size() - 1;
        List<ConditionNode> result = new ArrayList<ConditionNode>();
        for (int i = 0; i < units.size(); i++) {
            ConditionNode unit = units.get(i);
            List<ComparisonNode> comparisons = new ArrayList<ComparisonNode>();
            if (i == 0) {
                ComparisonNode lower = extractLowerBound(unit);
                if (lower != null) {
                    comparisons.add(lower.copy());
                }
            } else if (i == lastIndex && lastNeedsUpper && globalMaxUpper != null) {
                comparisons.add(buildUpperBound(unit, globalMaxUpper));
            }
            comparisons.addAll(extractBusinessComparisons(unit));
            result.add(ConditionNode.minUnit(unit.getTableAlias(), unit.getTableName(), comparisons));
        }
        return result;
    }
    public static boolean isGloballyContradictory(List<ConditionNode> units) {
        if (units == null || units.size() <= 1) {
            return false;
        }
        String firstLower = extractLowerBoundValue(units.get(0));
        String globalMaxUpper = resolveGlobalMaxUpper(units);
        boolean lastNeedsUpper = lastUnitHasUpperType(units.get(units.size() - 1));
        if (firstLower == null || globalMaxUpper == null || !lastNeedsUpper) {
            return false;
        }
        return ComparisonValueUtils.compareValues(quote(firstLower), quote(globalMaxUpper)) > 0;
    }
    private static String resolveGlobalMaxUpper(List<ConditionNode> units) {
        String maxUpper = null;
        for (ConditionNode unit : units) {
            ComparisonNode etlMonth = extractEtlMonthComparison(unit);
            if (etlMonth == null) {
                continue;
            }
            String candidate = extractUpperCandidate(etlMonth);
            if (candidate == null) {
                continue;
            }
            if (maxUpper == null
                    || ComparisonValueUtils.compareValues(quote(candidate), quote(maxUpper)) > 0) {
                maxUpper = candidate;
            }
        }
        return maxUpper;
    }
    private static boolean lastUnitHasUpperType(ConditionNode unit) {
        ComparisonNode etlMonth = extractEtlMonthComparison(unit);
        if (etlMonth == null) {
            return false;
        }
        ComparisonOperator operator = etlMonth.getOperator();
        return operator == ComparisonOperator.LE
                || operator == ComparisonOperator.LT
                || operator == ComparisonOperator.EQ;
    }
    private static ComparisonNode extractLowerBound(ConditionNode unit) {
        ComparisonNode etlMonth = extractEtlMonthComparison(unit);
        if (etlMonth == null) {
            return null;
        }
        if (etlMonth.getOperator() == ComparisonOperator.GE
                || etlMonth.getOperator() == ComparisonOperator.GT) {
            return etlMonth;
        }
        return null;
    }
    private static String extractLowerBoundValue(ConditionNode unit) {
        ComparisonNode lower = extractLowerBound(unit);
        if (lower == null) {
            return null;
        }
        return ComparisonValueUtils.stripQuote(lower.getValue());
    }
    private static String extractUpperCandidate(ComparisonNode etlMonth) {
        if (etlMonth.getOperator() == ComparisonOperator.LE
                || etlMonth.getOperator() == ComparisonOperator.LT
                || etlMonth.getOperator() == ComparisonOperator.EQ) {
            return ComparisonValueUtils.stripQuote(etlMonth.getValue());
        }
        return null;
    }
    private static ComparisonNode buildUpperBound(ConditionNode unit, String upperValue) {
        ComparisonNode sample = extractEtlMonthComparison(unit);
        String alias = sample != null ? sample.getTableAlias() : unit.getTableAlias();
        return new ComparisonNode(alias, ETL_MONTH, ComparisonOperator.LE, quote(upperValue));
    }
    private static ComparisonNode extractEtlMonthComparison(ConditionNode unit) {
        for (ComparisonNode comparison : unit.getComparisons()) {
            if (ETL_MONTH.equalsIgnoreCase(comparison.getColumn())) {
                return comparison;
            }
        }
        return null;
    }
    private static List<ComparisonNode> extractBusinessComparisons(ConditionNode unit) {
        List<ComparisonNode> business = new ArrayList<ComparisonNode>();
        for (ComparisonNode comparison : unit.getComparisons()) {
            if (!ETL_MONTH.equalsIgnoreCase(comparison.getColumn())) {
                business.add(comparison.copy());
            }
        }
        return business;
    }
    private static String quote(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if ((trimmed.startsWith("'") && trimmed.endsWith("'"))
                || (trimmed.startsWith("\"") && trimmed.endsWith("\""))) {
            return trimmed;
        }
        return "'" + trimmed + "'";
    }
}