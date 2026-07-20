package com.chenxy.demo.sql.model;

/**
 * 条件校验结果类型，由 {@link com.chenxy.demo.sql.validator.ConditionValidator} 判定。
 *
 * @see com.chenxy.demo.sql.model.ValidationResult#isValid()
 */
public enum ConditionType {
    /** 语法错误或未知字段 */
    SYNTAX_ERROR,
    /** 逻辑矛盾，如 etl_month 无法由同一分区同时满足 */
    CONTRADICTION,
    /** 恒真条件，范围过大 */
    TAUTOLOGY,
    /** 有效可满足条件，可正常生成 SQL */
    SATISFIABLE,
    /** 暂时无法静态判定 */
    UNKNOWN
}
