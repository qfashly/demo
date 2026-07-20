package com.chenxy.demo.sql.optimizer;

import com.chenxy.demo.sql.model.ConditionNode;
import com.chenxy.demo.sql.validator.ColumnConditionMerger;
import com.chenxy.demo.sql.validator.MinUnitMergeHelper;
import com.chenxy.demo.sql.validator.SameTableEtlMonthContradictionChecker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 合并 etl_month 约束兼容的同表 MIN_UNIT。
 *
 * <p>当多个 MIN_UNIT 的 etl_month 可由<b>同一分区月份</b>同时满足时（如
 * {@code <= '2026-07-01'} 与 {@code >= '2026-03-01'}），将其合并为一个 MIN_UNIT，
 * 对应生成单个 jtb 子查询。
 *
 * <p>若合并后 etl_month 矛盾，则不合并（由 {@link SameTableEtlMonthContradictionChecker} 在全局判定）。
 */
public final class CompatibleMinUnitMergeOptimizer {

    private CompatibleMinUnitMergeOptimizer() {
    }

    /**
     * @return 合并后的 MIN_UNIT 列表（可能少于输入数量）
     */
    public static List<ConditionNode> merge(List<ConditionNode> units, ColumnConditionMerger merger) {
        if (units == null || units.size() <= 1) {
            return units;
        }
        if (SameTableEtlMonthContradictionChecker.isContradictory(units, merger)) {
            return units;
        }
        return MinUnitMergeHelper.mergeCompatible(units, merger);
    }
}
