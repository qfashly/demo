/**
 * 条件校验与优化层。
 *
 * <p>在生成 SQL 前对 AST 做列条件合并、同表 MIN_UNIT 处理、etl_month 矛盾检测。
 * 核心类：{@link com.chenxy.demo.sql.validator.ConditionValidator}
 */
package com.chenxy.demo.sql.validator;
