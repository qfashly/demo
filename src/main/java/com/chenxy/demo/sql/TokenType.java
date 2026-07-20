package com.chenxy.demo.sql;

import lombok.Getter;

/**
 * packageName com.scredit.crs.enums
 *
 * @author chenxy
 * @enumName TokenType
 * @date 14 7月 2026 16:14
 * @Version 1.0.0
 * @description TODO
 */
@Getter
public enum TokenType {
    IDENT("标识符(字母/数字/下划线组成，且不是 AND/OR)"),
    STRING("字符串字面量"),
    NUMBER("数字字面量"),
    OPERATOR("比较操作符(=、!=、>、>=、<、<=)"),
    AND("逻辑与(AND)"),
    OR("逻辑或(OR)"),
    LPAREN("左括号"),
    RPAREN("右括号"),
    DOT("点号"),
    COMMA("逗号"),
    EOF("输入结束"),;


    TokenType(String msg){
        this.msg = msg;
    }
    private final String msg;

    @Override
    public String toString() {
        return msg;
    }
}

