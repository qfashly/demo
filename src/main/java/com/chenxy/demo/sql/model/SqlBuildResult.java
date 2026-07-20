package com.chenxy.demo.sql.model;

import java.util.List;

/**
 * SQL 组装结果。
 *
 * <p>当 {@link #validationResult} 无效（矛盾/恒真/语法错误）时，{@code countSql} 与 {@code querySql} 为 null。
 */
public class SqlBuildResult {

    private ValidationResult validationResult;
    private String countSql;
    private String querySql;
    private List<String> selectList;

    public SqlBuildResult() {
    }

    public SqlBuildResult(ValidationResult validationResult, String countSql, String querySql, List<String> selectList) {
        this.validationResult = validationResult;
        this.countSql = countSql;
        this.querySql = querySql;
        this.selectList = selectList;
    }

    public ValidationResult getValidationResult() {
        return validationResult;
    }

    public void setValidationResult(ValidationResult validationResult) {
        this.validationResult = validationResult;
    }

    public String getCountSql() {
        return countSql;
    }

    public void setCountSql(String countSql) {
        this.countSql = countSql;
    }

    public String getQuerySql() {
        return querySql;
    }

    public void setQuerySql(String querySql) {
        this.querySql = querySql;
    }

    public List<String> getSelectList() {
        return selectList;
    }

    public void setSelectList(List<String> selectList) {
        this.selectList = selectList;
    }
}
