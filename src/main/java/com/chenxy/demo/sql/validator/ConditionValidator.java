package com.chenxy.demo.sql.validator;

import com.chenxy.demo.sql.model.ComparisonNode;
import com.chenxy.demo.sql.model.ComparisonOperator;
import com.chenxy.demo.sql.model.ConditionNode;
import com.chenxy.demo.sql.model.ConditionType;
import com.chenxy.demo.sql.model.ValidationResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 条件校验与优化器
 */
public class ConditionValidator {

    private final ColumnConditionMerger columnConditionMerger = new ColumnConditionMerger();

    public ValidationResult validate(ConditionNode root) {
        try {
            ConditionNode optimized = optimize(root);
            ConditionType type = analyzeConditionType(optimized);
            String message = buildMessage(type);
            if (type == ConditionType.SYNTAX_ERROR || type == ConditionType.CONTRADICTION) {
                return new ValidationResult(type, message, optimized);
            }
            return new ValidationResult(type, message, optimized);
        } catch (IllegalArgumentException ex) {
            return new ValidationResult(ConditionType.SYNTAX_ERROR, ex.getMessage(), root);
        }
    }

    public ConditionNode optimize(ConditionNode root) {
        ConditionNode normalized = normalize(root);
        return reorder(normalized);
    }

    private ConditionNode normalize(ConditionNode node) {
        if (node.getType() == ConditionNode.NodeType.AND || node.getType() == ConditionNode.NodeType.OR) {
            List<ConditionNode> children = new ArrayList<ConditionNode>();
            for (ConditionNode child : node.getChildren()) {
                children.add(normalize(child));
            }
            if (node.getType() == ConditionNode.NodeType.AND) {
                return ConditionNode.and(flattenAnd(children));
            }
            return ConditionNode.or(flattenOr(children));
        }
        if (node.getType() == ConditionNode.NodeType.MIN_UNIT) {
            List<ComparisonNode> merged = columnConditionMerger.merge(node.getComparisons());
            List<ComparisonNode> sorted = new ArrayList<ComparisonNode>(merged);
            Collections.sort(sorted, new Comparator<ComparisonNode>() {
                @Override
                public int compare(ComparisonNode o1, ComparisonNode o2) {
                    boolean o1Etl = "etl_month".equalsIgnoreCase(o1.getColumn());
                    boolean o2Etl = "etl_month".equalsIgnoreCase(o2.getColumn());
                    if (o1Etl != o2Etl) {
                        return o1Etl ? -1 : 1;
                    }
                    return o1.getColumn().compareTo(o2.getColumn());
                }
            });
            return ConditionNode.minUnit(node.getTableAlias(), node.getTableName(), sorted);
        }
        return node;
    }

    private ConditionNode reorder(ConditionNode node) {
        if (node.getType() == ConditionNode.NodeType.AND) {
            List<ConditionNode> children = new ArrayList<ConditionNode>();
            for (ConditionNode child : node.getChildren()) {
                children.add(reorder(child));
            }
            Collections.sort(children, new Comparator<ConditionNode>() {
                @Override
                public int compare(ConditionNode o1, ConditionNode o2) {
                    return nodeSortKey(o1).compareTo(nodeSortKey(o2));
                }
            });
            return ConditionNode.and(children);
        }
        if (node.getType() == ConditionNode.NodeType.OR) {
            List<ConditionNode> children = new ArrayList<ConditionNode>();
            for (ConditionNode child : node.getChildren()) {
                children.add(reorder(child));
            }
            Collections.sort(children, new Comparator<ConditionNode>() {
                @Override
                public int compare(ConditionNode o1, ConditionNode o2) {
                    return nodeSortKey(o1).compareTo(nodeSortKey(o2));
                }
            });
            return ConditionNode.or(children);
        }
        return node;
    }

    private List<ConditionNode> flattenAnd(List<ConditionNode> nodes) {
        List<ConditionNode> result = new ArrayList<ConditionNode>();
        for (ConditionNode node : nodes) {
            if (node.getType() == ConditionNode.NodeType.AND) {
                result.addAll(flattenAnd(node.getChildren()));
            } else {
                result.add(node);
            }
        }
        return mergeSameTableMinUnits(result);
    }

    private List<ConditionNode> flattenOr(List<ConditionNode> nodes) {
        List<ConditionNode> result = new ArrayList<ConditionNode>();
        for (ConditionNode node : nodes) {
            if (node.getType() == ConditionNode.NodeType.OR) {
                result.addAll(flattenOr(node.getChildren()));
            } else {
                result.add(node);
            }
        }
        return result;
    }

    private List<ConditionNode> mergeSameTableMinUnits(List<ConditionNode> nodes) {
        Map<String, ConditionNode> minUnits = new HashMap<String, ConditionNode>();
        List<ConditionNode> others = new ArrayList<ConditionNode>();
        for (ConditionNode node : nodes) {
            if (node.getType() == ConditionNode.NodeType.MIN_UNIT) {
                String alias = node.getTableAlias();
                if (minUnits.containsKey(alias)) {
                    ConditionNode existing = minUnits.get(alias);
                    List<ComparisonNode> merged = new ArrayList<ComparisonNode>(existing.getComparisons());
                    merged.addAll(node.getComparisons());
                    merged = columnConditionMerger.merge(merged);
                    String tableName = existing.getTableName() != null ? existing.getTableName() : node.getTableName();
                    minUnits.put(alias, ConditionNode.minUnit(alias, tableName, merged));
                } else {
                    minUnits.put(alias, node);
                }
            } else {
                others.add(node);
            }
        }
        List<ConditionNode> result = new ArrayList<ConditionNode>(others);
        result.addAll(minUnits.values());
        return result;
    }

    private ConditionType analyzeConditionType(ConditionNode root) {
        ConditionType type = analyzeNode(root);
        if (type == ConditionType.CONTRADICTION || type == ConditionType.TAUTOLOGY) {
            return type;
        }
        if (type == ConditionType.UNKNOWN) {
            return ConditionType.UNKNOWN;
        }
        return ConditionType.SATISFIABLE;
    }

    private ConditionType analyzeNode(ConditionNode node) {
        if (node.getType() == ConditionNode.NodeType.AND) {
            boolean hasUnknown = false;
            for (ConditionNode child : node.getChildren()) {
                ConditionType childType = analyzeNode(child);
                if (childType == ConditionType.CONTRADICTION) {
                    return ConditionType.CONTRADICTION;
                }
                if (childType == ConditionType.TAUTOLOGY) {
                    continue;
                }
                if (childType == ConditionType.UNKNOWN) {
                    hasUnknown = true;
                }
            }
            return hasUnknown ? ConditionType.UNKNOWN : ConditionType.SATISFIABLE;
        }
        if (node.getType() == ConditionNode.NodeType.OR) {
            boolean allContradiction = true;
            boolean hasTautology = false;
            boolean hasUnknown = false;
            for (ConditionNode child : node.getChildren()) {
                ConditionType childType = analyzeNode(child);
                if (childType == ConditionType.TAUTOLOGY) {
                    hasTautology = true;
                }
                if (childType != ConditionType.CONTRADICTION) {
                    allContradiction = false;
                }
                if (childType == ConditionType.UNKNOWN) {
                    hasUnknown = true;
                }
            }
            if (hasTautology || allContradiction && node.getChildren().size() > 1) {
                if (hasTautology) {
                    return ConditionType.TAUTOLOGY;
                }
            }
            if (allContradiction) {
                return ConditionType.CONTRADICTION;
            }
            return hasUnknown ? ConditionType.UNKNOWN : ConditionType.SATISFIABLE;
        }
        if (node.getType() == ConditionNode.NodeType.MIN_UNIT) {
            return analyzeMinUnit(node);
        }
        if (node.getType() == ConditionNode.NodeType.CROSS_TABLE
                || node.getType() == ConditionNode.NodeType.RAW) {
            return ConditionType.SATISFIABLE;
        }
        return ConditionType.UNKNOWN;
    }

    private ConditionType analyzeMinUnit(ConditionNode node) {
        Map<String, List<ComparisonNode>> columnMap = new HashMap<String, List<ComparisonNode>>();
        for (ComparisonNode comparison : node.getComparisons()) {
            if (!columnMap.containsKey(comparison.getColumn())) {
                columnMap.put(comparison.getColumn(), new ArrayList<ComparisonNode>());
            }
            columnMap.get(comparison.getColumn()).add(comparison);
        }
        for (Map.Entry<String, List<ComparisonNode>> entry : columnMap.entrySet()) {
            if (isContradictory(entry.getValue())) {
                return ConditionType.CONTRADICTION;
            }
            if (isTautology(entry.getValue())) {
                return ConditionType.TAUTOLOGY;
            }
        }
        return ConditionType.SATISFIABLE;
    }

    private boolean isContradictory(List<ComparisonNode> comparisons) {
        Set<String> eqValues = new HashSet<String>();
        Set<String> neValues = new HashSet<String>();
        Double lower = null;
        boolean lowerInclusive = false;
        Double upper = null;
        boolean upperInclusive = false;

        for (ComparisonNode comparison : comparisons) {
            if (comparison.getOperator() == ComparisonOperator.EQ) {
                String value = stripQuote(comparison.getValue());
                if (!eqValues.isEmpty() && !eqValues.contains(value)) {
                    return true;
                }
                eqValues.add(value);
                if (neValues.contains(value)) {
                    return true;
                }
            } else if (comparison.getOperator() == ComparisonOperator.NE) {
                neValues.add(stripQuote(comparison.getValue()));
            } else {
                Double numeric = tryParseNumber(comparison.getValue());
                if (numeric == null) {
                    continue;
                }
                switch (comparison.getOperator()) {
                    case GT:
                        if (lower == null || numeric > lower) {
                            lower = numeric;
                            lowerInclusive = false;
                        }
                        break;
                    case GE:
                        if (lower == null || numeric > lower || (numeric.equals(lower) && !lowerInclusive)) {
                            lower = numeric;
                            lowerInclusive = true;
                        }
                        break;
                    case LT:
                        if (upper == null || numeric < upper) {
                            upper = numeric;
                            upperInclusive = false;
                        }
                        break;
                    case LE:
                        if (upper == null || numeric < upper || (numeric.equals(upper) && !upperInclusive)) {
                            upper = numeric;
                            upperInclusive = true;
                        }
                        break;
                    default:
                        break;
                }
            }
        }

        if (!eqValues.isEmpty()) {
            for (String eq : eqValues) {
                Double numeric = tryParseNumber("'" + eq + "'");
                if (numeric != null) {
                    if (lower != null && (numeric < lower || (numeric.equals(lower) && !lowerInclusive))) {
                        return true;
                    }
                    if (upper != null && (numeric > upper || (numeric.equals(upper) && !upperInclusive))) {
                        return true;
                    }
                }
            }
        }
        if (lower != null && upper != null) {
            if (lower > upper) {
                return true;
            }
            if (lower.equals(upper) && !(lowerInclusive && upperInclusive)) {
                return true;
            }
        }
        return false;
    }

    private boolean isTautology(List<ComparisonNode> comparisons) {
        if (comparisons.size() < 2) {
            return false;
        }
        for (ComparisonNode left : comparisons) {
            for (ComparisonNode right : comparisons) {
                if (left == right) {
                    continue;
                }
                if (left.getOperator() == ComparisonOperator.EQ && right.getOperator() == ComparisonOperator.NE) {
                    if (left.getValue().equals(right.getValue())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private Double tryParseNumber(String value) {
        if (value == null) {
            return null;
        }
        String text = stripQuote(value);
        try {
            return Double.valueOf(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String stripQuote(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        if ((text.startsWith("'") && text.endsWith("'")) || (text.startsWith("\"") && text.endsWith("\""))) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

    private String nodeSortKey(ConditionNode node) {
        if (node.getType() == ConditionNode.NodeType.MIN_UNIT) {
            return "2_" + node.getTableAlias();
        }
        if (node.getType() == ConditionNode.NodeType.CROSS_TABLE) {
            return "3_cross";
        }
        if (node.getType() == ConditionNode.NodeType.RAW) {
            return "4_raw";
        }
        return "0_" + node.getType().name();
    }

    private String buildMessage(ConditionType type) {
        switch (type) {
            case SYNTAX_ERROR:
                return "条件存在语法错误";
            case CONTRADICTION:
                return "条件存在逻辑矛盾，无法满足";
            case TAUTOLOGY:
                return "条件恒真，请缩小查询范围";
            case SATISFIABLE:
                return "条件有效，可正常查询";
            default:
                return "条件暂时无法判定，请补充更明确的条件";
        }
    }
}
