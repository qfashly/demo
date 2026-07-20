package com.chenxy.demo.sql.optimizer;

import com.chenxy.demo.sql.model.ConditionNode;
import com.chenxy.demo.sql.validator.ColumnConditionMerger;
import com.chenxy.demo.sql.validator.SameTableEtlMonthContradictionChecker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SQL 结构优化器：在条件校验通过后、生成 SQL 前，对条件 AST 做结构优化。
 *
 * <p>当前实现的优化项：
 * <ul>
 *   <li><b>同表 MIN_UNIT 合并</b>：etl_month 兼容的多个 MIN_UNIT 合并为一个，减少 jtb 子查询数量</li>
 * </ul>
 *
 * <p>调用时机：{@link com.chenxy.demo.sql.validator.ConditionValidator#optimize}
 */
public final class SqlQueryOptimizer {

    private SqlQueryOptimizer() {
    }

    /**
     * 对条件树执行 SQL 结构优化。
     */
    public static ConditionNode optimize(ConditionNode root, ColumnConditionMerger merger) {
        if (root == null) {
            return null;
        }
        if (root.getType() == ConditionNode.NodeType.AND) {
            List<ConditionNode> optimizedChildren = new ArrayList<ConditionNode>();
            for (ConditionNode child : root.getChildren()) {
                optimizedChildren.add(optimize(child, merger));
            }
            return ConditionNode.and(optimizeAndChildren(optimizedChildren, merger));
        }
        if (root.getType() == ConditionNode.NodeType.OR) {
            List<ConditionNode> optimizedChildren = new ArrayList<ConditionNode>();
            for (ConditionNode child : root.getChildren()) {
                optimizedChildren.add(optimize(child, merger));
            }
            return ConditionNode.or(optimizedChildren);
        }
        return root;
    }

    /**
     * 在同一 AND 层级，按表别名分组并合并兼容的 MIN_UNIT。
     */
    static List<ConditionNode> optimizeAndChildren(List<ConditionNode> children, ColumnConditionMerger merger) {
        Map<String, List<ConditionNode>> minUnitsByAlias = new HashMap<String, List<ConditionNode>>();
        List<ConditionNode> others = new ArrayList<ConditionNode>();
        for (ConditionNode node : children) {
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
        for (List<ConditionNode> units : minUnitsByAlias.values()) {
            if (SameTableEtlMonthContradictionChecker.isContradictory(units, merger)) {
                result.addAll(units);
            } else {
                result.addAll(CompatibleMinUnitMergeOptimizer.merge(units, merger));
            }
        }
        return result;
    }
}
