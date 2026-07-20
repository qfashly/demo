package com.chenxy.demo.sql.validator;

import com.chenxy.demo.sql.model.ConditionNode;
import com.chenxy.demo.sql.optimizer.CompatibleMinUnitMergeOptimizer;

import java.util.List;

/**
 * 同表多个 MIN_UNIT 的处理策略。
 *
 * <p>当 AND 下同一表别名存在多个 MIN_UNIT（通常来自不同括号分组）时：
 * <ol>
 *   <li>若 etl_month 矛盾 → 保持原样，由 {@link ConditionValidator} 返回 CONTRADICTION</li>
 *   <li>若 etl_month 兼容 → 委托 {@link CompatibleMinUnitMergeOptimizer} 合并为一个 MIN_UNIT（一个 jtb）</li>
 * </ol>
 */
public final class SameTableMinUnitProcessor {
    private SameTableMinUnitProcessor() {
    }

    public static List<ConditionNode> process(List<ConditionNode> units, ColumnConditionMerger merger) {
        return CompatibleMinUnitMergeOptimizer.merge(units, merger);
    }
}
