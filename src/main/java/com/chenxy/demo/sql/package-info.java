/**
 * SQL 条件查询组装模块。
 *
 * <p>将用户输入的多表筛选条件解析、校验并生成 count/query SQL。
 * 详细设计说明见项目文档：{@code docs/sql-query-builder.md}
 *
 * <p>推荐入口：{@link com.chenxy.demo.sql.SqlQueryService}
 *
 * @see com.chenxy.demo.sql.parser.ConditionParser
 * @see com.chenxy.demo.sql.validator.ConditionValidator
 * @see com.chenxy.demo.sql.builder.SqlQueryBuilder
 */
package com.chenxy.demo.sql;
