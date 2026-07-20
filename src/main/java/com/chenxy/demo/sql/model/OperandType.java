package com.chenxy.demo.sql.model;

/**
 * 右操作数类型
 */
public enum OperandType {
    /** 字面量 */
    LITERAL,
    /** 字段引用（跨表比较） */
    FIELD,
    /** 函数/子查询等表达式 */
    EXPRESSION
}
