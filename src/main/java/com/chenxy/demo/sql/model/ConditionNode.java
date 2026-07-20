package com.chenxy.demo.sql.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 条件抽象语法树（AST）节点。
 *
 * <p>解析器将用户输入的 condition 字符串转换为此树；校验器与 SQL 组装器均基于此树工作。
 *
 * <p>典型结构示例：
 * <pre>
 * AND
 * ├── MIN_UNIT(t1): [etl_month='2026-07-01', soc_pay_per_num_2m>=0]
 * └── MIN_UNIT(t2): [etl_month=(select max(...))]
 * </pre>
 */
public class ConditionNode {

    /**
     * 条件节点类型。
     */
    public enum NodeType {
        /** 逻辑与 */
        AND,
        /** 逻辑或；OR 分支在 SQL 层通过 UNION 实现 */
        OR,
        /**
         * 最小查询单元：同一表别名下一组比较条件。
         * <ul>
         *   <li>必须包含 {@code etl_month} 条件</li>
         *   <li>业务字段可选；无业务字段时 jtb 输出 tableInfos 全部列</li>
         *   <li>每个 MIN_UNIT 对应一个 jtb 子查询</li>
         * </ul>
         */
        MIN_UNIT,
        /** 解析中间态：单条比较，归并阶段会转为 MIN_UNIT 或 CROSS_TABLE */
        COMPARISON,
        /** 跨表比较（如 t1.c1 = t2.c2），拼入主查询 WHERE，不参与 jtb 子查询 */
        CROSS_TABLE,
        /** 函数/复杂表达式，原样拼入 SQL */
        RAW
    }

    private NodeType type;
    private List<ConditionNode> children = new ArrayList<ConditionNode>();
    private String tableAlias;
    private String tableName;
    private List<ComparisonNode> comparisons = new ArrayList<ComparisonNode>();
    private ComparisonNode comparison;
    /** RAW 节点 SQL 片段 */
    private String rawSql;

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

    public static ConditionNode crossTable(ComparisonNode comparison) {
        ConditionNode node = new ConditionNode();
        node.type = NodeType.CROSS_TABLE;
        node.comparison = comparison;
        return node;
    }

    public static ConditionNode raw(String rawSql) {
        ConditionNode node = new ConditionNode();
        node.type = NodeType.RAW;
        node.rawSql = rawSql;
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

    public String getRawSql() {
        return rawSql;
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
