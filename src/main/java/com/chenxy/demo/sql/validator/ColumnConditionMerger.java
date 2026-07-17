package com.chenxy.demo.sql.validator;


import com.chenxy.demo.sql.model.ComparisonNode;
import com.chenxy.demo.sql.model.ComparisonOperator;
import com.chenxy.demo.sql.model.OperandType;

import java.util.*;

/**
 * packageName com.scredit.crs.sql.validator
 *
 * @author chenxy
 * @className ColumnConditionMerger
 * @date 17 7月 2026 09:21
 * @Version 1.0.0
 * @description TODO
 */
public class ColumnConditionMerger {
    private final String etlMonthColumn;
    public ColumnConditionMerger() {
        this("etl_month");
    }
    public ColumnConditionMerger(String etlMonthColumn) {
        this.etlMonthColumn = etlMonthColumn;
    }
    public List<ComparisonNode> merge(List<ComparisonNode> comparisons) {
        if (comparisons == null || comparisons.size() <= 1) {
            return comparisons;
        }
        Map<String, List<ComparisonNode>> grouped = new LinkedHashMap<String, List<ComparisonNode>>();
        for (ComparisonNode comparison : comparisons) {
            String column = comparison.getColumn();
            if (!grouped.containsKey(column)) {
                grouped.put(column, new ArrayList<ComparisonNode>());
            }
            grouped.get(column).add(comparison);
        }
        List<ComparisonNode> merged = new ArrayList<ComparisonNode>();
        for (Map.Entry<String, List<ComparisonNode>> entry : grouped.entrySet()) {
            merged.addAll(mergeSameColumn(entry.getKey(), entry.getValue()));
        }
        return merged;
    }
    private List<ComparisonNode> mergeSameColumn(String column, List<ComparisonNode> list) {
        if (list.size() <= 1) {
            return list;
        }
        if (etlMonthColumn.equalsIgnoreCase(column)) {
            return mergeEtlMonth(list);
        }
        return dedupeIdentical(list);
    }

    private List<ComparisonNode> mergeEtlMonth(List<ComparisonNode> list) {
        ComparisonNode sample = list.get(0);
        Set<String> eqValues = new LinkedHashSet<String>();
        Set<String> inValues = new LinkedHashSet<String>();
        Set<String> notInValues = new LinkedHashSet<String>();
        String betweenLower = null;
        String betweenUpper = null;
        String lowerBound = null;
        boolean lowerInclusive = false;
        String upperBound = null;
        boolean upperInclusive = false;
        List<ComparisonNode> others = new ArrayList<ComparisonNode>();
        for (ComparisonNode node : list) {
            if (!ComparisonValueUtils.hasLiteralRhs(node)) {
                others.add(node);
                continue;
            }
            switch (node.getOperator()) {
                case EQ:
                    eqValues.add(normalizeValue(node.getRhsSql()));
                    break;
                case IN:
                    inValues.addAll(splitValues(node.getInOperand()));
                    break;
                case NOT_IN:
                    notInValues.addAll(splitValues(node.getInOperand()));
                    break;
                case BETWEEN:
                    betweenLower = node.getValue();
                    betweenUpper = node.getBetweenUpper();
                    break;
                case GE:
                    LowerBound mergedLower = mergeLowerBound(lowerBound, lowerInclusive, node.getValue(), true);
                    lowerBound = mergedLower.value;
                    lowerInclusive = mergedLower.inclusive;
                    break;
                case GT:
                    mergedLower = mergeLowerBound(lowerBound, lowerInclusive, node.getValue(), false);
                    lowerBound = mergedLower.value;
                    lowerInclusive = mergedLower.inclusive;
                    break;
                case LE:
                    UpperBound mergedUpper = mergeUpperBound(upperBound, upperInclusive, node.getValue(), true);
                    upperBound = mergedUpper.value;
                    upperInclusive = mergedUpper.inclusive;
                    break;
                case LT:
                    mergedUpper = mergeUpperBound(upperBound, upperInclusive, node.getValue(), false);
                    upperBound = mergedUpper.value;
                    upperInclusive = mergedUpper.inclusive;
                    break;
                default:
                    others.add(node);
                    break;
            }
        }
        if (!eqValues.isEmpty()) {
            inValues.addAll(eqValues);
        }
        List<ComparisonNode> result = new ArrayList<ComparisonNode>();
        if (betweenLower != null && betweenUpper != null) {
            if (ComparisonValueUtils.isValidClosedRange(betweenLower, betweenUpper)) {
                result.add(buildComparison(sample, ComparisonOperator.BETWEEN, betweenLower, betweenUpper));
            } else {
                result.add(buildComparison(sample, ComparisonOperator.GE, betweenLower, null));
                result.add(buildComparison(sample, ComparisonOperator.LE, betweenUpper, null));
            }
        } else if (lowerBound != null && upperBound != null && lowerInclusive && upperInclusive
                && ComparisonValueUtils.isValidClosedRange(lowerBound, upperBound)) {
            result.add(buildComparison(sample, ComparisonOperator.BETWEEN, lowerBound, upperBound));
        } else {
            if (lowerBound != null) {
                result.add(buildComparison(sample,
                        lowerInclusive ? ComparisonOperator.GE : ComparisonOperator.GT, lowerBound, null));
            }
            if (upperBound != null) {
                result.add(buildComparison(sample,
                        upperInclusive ? ComparisonOperator.LE : ComparisonOperator.LT, upperBound, null));
            }
        }
        if (!inValues.isEmpty()) {
            if (inValues.size() == 1) {
                result.add(buildComparison(sample, ComparisonOperator.EQ, inValues.iterator().next(), null));
            } else {
                result.add(buildInComparison(sample, ComparisonOperator.IN, inValues));
            }
        }
        if (!notInValues.isEmpty()) {
            result.add(buildInComparison(sample, ComparisonOperator.NOT_IN, notInValues));
        }
        result.addAll(others);
        return dedupeIdentical(result);
    }

    private ComparisonNode buildComparison(ComparisonNode sample, ComparisonOperator operator,
                                           String value, String betweenUpper) {
        ComparisonNode node = new ComparisonNode(sample.getTableAlias(), sample.getColumn(), operator, value);
        node.setBetweenUpper(betweenUpper);
        node.setRightOperandType(OperandType.LITERAL);
        node.setLeftExpression(sample.getLeftExpression());
        if (operator == ComparisonOperator.IN || operator == ComparisonOperator.NOT_IN) {
            node.setInValues(true);
        }
        return node;
    }
    private ComparisonNode buildInComparison(ComparisonNode sample, ComparisonOperator operator, Set<String> values) {
        ComparisonNode node = new ComparisonNode(sample.getTableAlias(), sample.getColumn(), operator,
                joinValues(values));
        node.setInValues(true);
        return node;
    }
    private List<ComparisonNode> dedupeIdentical(List<ComparisonNode> list) {
        List<ComparisonNode> result = new ArrayList<ComparisonNode>();
        for (ComparisonNode node : list) {
            if (!containsSame(result, node)) {
                result.add(node);
            }
        }
        return result;
    }
    private boolean containsSame(List<ComparisonNode> list, ComparisonNode target) {
        for (ComparisonNode node : list) {
            if (isSameComparison(node, target)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSameComparison(ComparisonNode a, ComparisonNode b) {
        if (!a.getColumn().equalsIgnoreCase(b.getColumn())) {
            return false;
        }
        if (a.getOperator() != b.getOperator()) {
            return false;
        }
        if (a.getOperator() == ComparisonOperator.BETWEEN) {
            return normalizeValue(a.getRhsSql()).equals(normalizeValue(b.getRhsSql()))
                    && normalizeValue(a.getBetweenUpper()).equals(normalizeValue(b.getBetweenUpper()));
        }
        if (a.getOperator() == ComparisonOperator.IN || a.getOperator() == ComparisonOperator.NOT_IN || a.isInValues()) {
            return normalizeValue(a.getInOperand()).equals(normalizeValue(b.getInOperand()));
        }
        if (a.getRightOperandType() != b.getRightOperandType()) {
            return false;
        }
        return normalizeValue(a.getRhsSql()).equals(normalizeValue(b.getRhsSql()));
    }
    private Set<String> splitValues(String raw) {
        Set<String> values = new LinkedHashSet<String>();
        if (raw == null) {
            return values;
        }
        String[] parts = raw.split(",");
        for (String part : parts) {
            values.add(part.trim());
        }
        return values;
    }
    private String joinValues(Set<String> values) {
        StringBuilder sb = new StringBuilder();
        for (String value : values) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(value);
        }
        return sb.toString();
    }

    private String normalizeValue(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    private LowerBound mergeLowerBound(String current, boolean currentInclusive, String candidate, boolean candidateInclusive) {
        if (current == null) {
            return new LowerBound(candidate, candidateInclusive);
        }
        int cmp = ComparisonValueUtils.compareValues(candidate, current);
        if (cmp > 0) {
            return new LowerBound(candidate, candidateInclusive);
        }
        if (cmp == 0 && !candidateInclusive && currentInclusive) {
            return new LowerBound(current, false);
        }
        return new LowerBound(current, currentInclusive);
    }

    private UpperBound mergeUpperBound(String current, boolean currentInclusive, String candidate, boolean candidateInclusive) {
        if (current == null) {
            return new UpperBound(candidate, candidateInclusive);
        }
        int cmp = ComparisonValueUtils.compareValues(candidate, current);
        if (cmp < 0) {
            return new UpperBound(candidate, candidateInclusive);
        }
        if (cmp == 0 && !candidateInclusive && currentInclusive) {
            return new UpperBound(current, false);
        }
        return new UpperBound(current, currentInclusive);
    }
    private static final class LowerBound {
        private final String value;
        private final boolean inclusive;
        private LowerBound(String value, boolean inclusive) {
            this.value = value;
            this.inclusive = inclusive;
        }
    }
    private static final class UpperBound {
        private final String value;
        private final boolean inclusive;
        private UpperBound(String value, boolean inclusive) {
            this.value = value;
            this.inclusive = inclusive;
        }
    }
}