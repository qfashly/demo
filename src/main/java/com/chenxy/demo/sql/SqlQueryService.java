package com.chenxy.demo.sql;


import com.chenxy.demo.sql.builder.SqlQueryBuilder;
import com.chenxy.demo.sql.model.*;
import com.chenxy.demo.sql.parser.ConditionParser;
import com.chenxy.demo.sql.validator.ConditionValidator;

import java.util.List;

/**
 * SQL 查询组装入口服务
 */
public class SqlQueryService {

    private final ConditionParser parser;
    private final ConditionValidator validator;
    private final SqlQueryBuilder builder;

    public SqlQueryService(List<TableInfo> tableInfos) {
        this(tableInfos, new SqlQueryConfig());
    }

    public SqlQueryService(List<TableInfo> tableInfos, SqlQueryConfig config) {
        this.parser = new ConditionParser(tableInfos);
        this.validator = new ConditionValidator();
        this.builder = new SqlQueryBuilder(tableInfos, config);
    }

    /**
     * 解析、校验并组装 count 与 query 两条 SQL
     */
    public SqlBuildResult build(String condition) {
        ConditionNode parsed = parser.parse(condition);
        ValidationResult validationResult = validator.validate(parsed);
        if (!validationResult.isValid()) {
            return new SqlBuildResult(validationResult, null, null, null);
        }
        ConditionNode optimized = validationResult.getOptimizedCondition();
        if (validationResult.getConditionType() == ConditionType.TAUTOLOGY) {
            return new SqlBuildResult(validationResult, null, null, null);
        }
        String countSql = builder.buildCountSql(optimized);
        String querySql = builder.buildQuerySql(optimized);
        List<String> buildSelectList = builder.buildSelectList(optimized);
        return new SqlBuildResult(validationResult, countSql, querySql, buildSelectList);
    }

    /**
     * 仅校验与优化条件
     */
    public ValidationResult validate(String condition) {
        ConditionNode parsed = parser.parse(condition);
        return validator.validate(parsed);
    }
}
