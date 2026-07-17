package com.chenxy.demo.sql.validator;

import com.chenxy.demo.sql.model.ComparisonNode;
import com.chenxy.demo.sql.model.ComparisonOperator;
import com.chenxy.demo.sql.model.OperandType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 合并同一字段的重复/可归并条件，尤其 etl_month
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
            switch (node.getOperator()) {
                case EQ:
                    eqValues.add(normalizeValue(node.getValue()));
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
                    lowerBound = node.getValue();
                    lowerInclusive = true;
                    break;
                case GT:
                    lowerBound = node.getValue();
                    lowerInclusive = false;
                    break;
                case LE:
                    upperBound = node.getValue();
                    upperInclusive = true;
                    break;
                case LT:
                    upperBound = node.getValue();
                    upperInclusive = false;
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
            result.add(buildComparison(sample, ComparisonOperator.BETWEEN, betweenLower, betweenUpper));
        } else if (lowerBound != null && upperBound != null && lowerInclusive && upperInclusive) {
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
        node.setRightOperandType(sample.getRightOperandType());
        node.setRightExpression(sample.getRightExpression());
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
            return normalizeValue(a.getValue()).equals(normalizeValue(b.getValue()))
                    && normalizeValue(a.getBetweenUpper()).equals(normalizeValue(b.getBetweenUpper()));
        }
        if (a.getOperator() == ComparisonOperator.IN || a.getOperator() == ComparisonOperator.NOT_IN || a.isInValues()) {
            return normalizeValue(a.getInOperand()).equals(normalizeValue(b.getInOperand()));
        }
        return normalizeValue(a.getValue()).equals(normalizeValue(b.getValue()));
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
}
