package com.chenxy.demo.sql.model;

/**
 * 条件校验结果类型
 */
public enum ConditionType {
    /** 语法错误 */
    SYNTAX_ERROR,
    /** 逻辑矛盾 */
    CONTRADICTION,
    /** 恒真条件 */
    TAUTOLOGY,
    /** 有意义的可满足条件 */
    SATISFIABLE,
    /** 无法判定 */
    UNKNOWN
}
