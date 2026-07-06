package com.chenxy.demo.sql.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 条件抽象语法树节点
 */
public class ConditionNode {

    public enum NodeType {
        AND,
        OR,
        /** 最小查询条件单元：同一表的 etl_month 与业务字段条件 */
        MIN_UNIT,
        COMPARISON
    }

    private NodeType type;
    private List<ConditionNode> children = new ArrayList<ConditionNode>();
    /** MIN_UNIT 时存放同一表的多条比较条件 */
    private String tableAlias;
    private String tableName;
    private List<ComparisonNode> comparisons = new ArrayList<ComparisonNode>();
    /** COMPARISON 叶子节点 */
    private ComparisonNode comparison;

    public static ConditionNode and(List<ConditionNode> children) {
        ConditionNode node = new ConditionNode();
        node.type = NodeType.AND;
        node.children = new ArrayList<ConditionNode>(children);
        return node;
    }

    public static ConditionNode or(List<ConditionNode> children) {
        ConditionNode node = new ConditionNode();
        node.type = NodeType.OR;
        node.children = new ArrayList<ConditionNode>(children);
        return node;
    }

    public static ConditionNode minUnit(String tableAlias, String tableName, List<ComparisonNode> comparisons) {
        ConditionNode node = new ConditionNode();
        node.type = NodeType.MIN_UNIT;
        node.tableAlias = tableAlias;
        node.tableName = tableName;
        node.comparisons = new ArrayList<ComparisonNode>(comparisons);
        return node;
    }

    public static ConditionNode comparison(ComparisonNode comparison) {
        ConditionNode node = new ConditionNode();
        node.type = NodeType.COMPARISON;
        node.comparison = comparison;
        return node;
    }

    public NodeType getType() {
        return type;
    }

    public List<ConditionNode> getChildren() {
        return children;
    }

    public String getTableAlias() {
        return tableAlias;
    }

    public String getTableName() {
        return tableName;
    }

    public List<ComparisonNode> getComparisons() {
        return comparisons;
    }

    public ComparisonNode getComparison() {
        return comparison;
    }

    public void setChildren(List<ConditionNode> children) {
        this.children = children;
    }

    public void setComparisons(List<ComparisonNode> comparisons) {
        this.comparisons = comparisons;
    }

    public List<ConditionNode> flattenSameType() {
        if (type != NodeType.AND && type != NodeType.OR) {
            return Collections.singletonList(this);
        }
        List<ConditionNode> flattened = new ArrayList<ConditionNode>();
        for (ConditionNode child : children) {
            if (child.getType() == type) {
                flattened.addAll(child.flattenSameType());
            } else {
                flattened.add(child);
            }
        }
        return flattened;
    }
}
