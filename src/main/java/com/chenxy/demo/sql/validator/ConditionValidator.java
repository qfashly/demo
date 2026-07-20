package com.chenxy.demo.sql.validator;


import com.chenxy.demo.sql.model.*;
import com.chenxy.demo.sql.optimizer.SqlQueryOptimizer;

import java.util.*;

/**
 * 条件校验与优化器。
 *
 * <p>主要职责：
 * <ul>
 *   <li><b>normalize</b>：展平 AND/OR、合并同列条件、处理同表 MIN_UNIT</li>
 *   <li><b>reorder</b>：稳定排序，保证相同条件生成相同 SQL</li>
 *   <li><b>analyze</b>：检测矛盾（CONTRADICTION）、恒真（TAUTOLOGY）</li>
 * </ul>
 *
 * <p>etl_month 矛盾判定：同表多个 MIN_UNIT 的 etl_month 合并后，
 * 若无法用单一分区月份满足，则返回 {@link com.chenxy.demo.sql.model.ConditionType#CONTRADICTION}。
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
        ConditionNode sqlOptimized = SqlQueryOptimizer.optimize(normalized, columnConditionMerger);
        return reorder(sqlOptimized);
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

    /**
     * 展平 AND 节点并将相邻的同表 MIN_UNIT 交给 {@link SameTableMinUnitProcessor}。
     */
    private List<ConditionNode> mergeSameTableMinUnits(List<ConditionNode> nodes) {
        Map<String, List<ConditionNode>> minUnitsByAlias = new HashMap<String, List<ConditionNode>>();
        List<ConditionNode> others = new ArrayList<ConditionNode>();
        for (ConditionNode node : nodes) {
            if (node.getType() == ConditionNode.NodeType.MIN_UNIT) {
                String alias = node.getTableAlias();
                if (!minUnitsByAlias.containsKey(alias)) {
                    minUnitsByAlias.put(alias, new ArrayList<ConditionNode>());
                }
                minUnitsByAlias.get(alias).add(node);
            } else {
                others.add(node);
            }
        }
        List<ConditionNode> result = new ArrayList<ConditionNode>(others);
        for (Map.Entry<String, List<ConditionNode>> entry : minUnitsByAlias.entrySet()) {
            result.addAll(SameTableMinUnitProcessor.process(entry.getValue(), columnConditionMerger));
        }
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
            Map<String, List<ConditionNode>> sameTableUnits = new HashMap<String, List<ConditionNode>>();
            for (ConditionNode child : node.getChildren()) {
                if (child.getType() == ConditionNode.NodeType.MIN_UNIT) {
                    String alias = child.getTableAlias();
                    if (!sameTableUnits.containsKey(alias)) {
                        sameTableUnits.put(alias, new ArrayList<ConditionNode>());
                    }
                    sameTableUnits.get(alias).add(child);
                }
            }
            for (List<ConditionNode> units : sameTableUnits.values()) {
                if (SameTableEtlMonthContradictionChecker.isContradictory(units, columnConditionMerger)) {
                    return ConditionType.CONTRADICTION;
                }
            }
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

    static boolean areEtlMonthConstraintsContradictory(List<ComparisonNode> comparisons) {
        return new ConditionValidator().isContradictory(comparisons);
    }
    boolean isEtlMonthContradictory(List<ComparisonNode> comparisons) {
        return isContradictory(comparisons);
    }

    /**
     * 判断一组比较条件是否逻辑矛盾。
     * <p>仅对<b>字面量</b>右操作数做静态分析；含子查询/函数表达式时跳过，避免误判。
     */
    private boolean isContradictory(List<ComparisonNode> comparisons) {
        List<ComparisonNode> literalComparisons = new ArrayList<ComparisonNode>();
        for (ComparisonNode comparison : comparisons) {
            if (ComparisonValueUtils.hasLiteralRhs(comparison)) {
                literalComparisons.add(comparison);
            }
        }
        if (literalComparisons.size() <= 1) {
            return false;
        }
        return isLiteralContradictory(literalComparisons);
    }

    private boolean isLiteralContradictory(List<ComparisonNode> comparisons) {
        Set<String> eqValues = new HashSet<String>();
        Set<String> neValues = new HashSet<String>();
        RangeBound lower = new RangeBound();
        RangeBound upper = new RangeBound();

        for (ComparisonNode comparison : comparisons) {
            if (comparison.getOperator() == ComparisonOperator.BETWEEN) {
                if (!ComparisonValueUtils.isValidClosedRange(comparison.getValue(), comparison.getBetweenUpper())) {
                    return true;
                }
                lower.merge(comparison.getValue(), true);
                upper.mergeUpper(comparison.getBetweenUpper(), true);
                continue;
            }
            if (comparison.getOperator() == ComparisonOperator.EQ) {
                String value = ComparisonValueUtils.stripQuote(comparison.getRhsSql());
                if (!eqValues.isEmpty() && !eqValues.contains(value)) {
                    return true;
                }
                eqValues.add(value);
                if (neValues.contains(value)) {
                    return true;
                }
            } else if (comparison.getOperator() == ComparisonOperator.NE) {
                neValues.add(ComparisonValueUtils.stripQuote(comparison.getRhsSql()));
            } else {
                switch (comparison.getOperator()) {
                    case GT:
                        lower.merge(comparison.getValue(), false);
                        break;
                    case GE:
                        lower.merge(comparison.getValue(), true);
                        break;
                    case LT:
                        upper.mergeUpper(comparison.getValue(), false);
                        break;
                    case LE:
                        upper.mergeUpper(comparison.getValue(), true);
                        break;
                    default:
                        break;
                }
            }
        }

        if (!eqValues.isEmpty()) {
            for (String eq : eqValues) {
                String quoted = "'" + eq + "'";
                if (lower.isSet() && !lower.allowsLower(quoted)) {
                    return true;
                }
                if (upper.isSet() && !upper.allowsUpper(quoted)) {
                    return true;
                }
            }
        }
        if (lower.isSet() && upper.isSet()) {
            return !ComparisonValueUtils.isValidOpenEndedRange(
                    lower.value, lower.inclusive, upper.value, upper.inclusive);
        }
        return false;
    }

    private static final class RangeBound {
        private String value;
        private boolean inclusive;
        private boolean isSet() {
            return value != null;
        }
        private void merge(String candidate, boolean candidateInclusive) {
            if (value == null) {
                value = candidate;
                inclusive = candidateInclusive;
                return;
            }
            int cmp = ComparisonValueUtils.compareValues(candidate, value);
            if (cmp > 0) {
                value = candidate;
                inclusive = candidateInclusive;
            } else if (cmp == 0 && !candidateInclusive && inclusive) {
                inclusive = false;
            }
        }
        private void mergeUpper(String candidate, boolean candidateInclusive) {
            if (value == null) {
                value = candidate;
                inclusive = candidateInclusive;
                return;
            }
            int cmp = ComparisonValueUtils.compareValues(candidate, value);
            if (cmp < 0) {
                value = candidate;
                inclusive = candidateInclusive;
            } else if (cmp == 0 && !candidateInclusive && inclusive) {
                inclusive = false;
            }
        }



        private boolean allowsLower(String actual) {
            int cmp = ComparisonValueUtils.compareValues(actual, value);
            if (cmp > 0) {
                return true;
            }
            if (cmp < 0) {
                return false;
            }
            return inclusive;
        }

        private boolean allowsUpper(String actual) {
            int cmp = ComparisonValueUtils.compareValues(actual, value);
            if (cmp < 0) {
                return true;
            }
            if (cmp > 0) {
                return false;
            }
            return inclusive;
        }
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

    public String buildMessage2(ConditionType type) {
        switch (type) {
            case SYNTAX_ERROR:
                return "条件组合存在语法错误";
            case CONTRADICTION:
                return "条件组合存在逻辑矛盾，无法满足";
            case TAUTOLOGY:
                return "条件组合恒真，请缩小查询范围";
            case SATISFIABLE:
                return "";
            default:
                return "条件组合暂时无法判定，请补充更明确的条件";
        }
    }
}
