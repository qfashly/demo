/**
 * 条件表达式解析层。
 *
 * <p>{@link com.chenxy.demo.sql.parser.ConditionParser} 负责：
 * <ol>
 *   <li>词法分析（tokenize）</li>
 *   <li>递归下降语法分析（AND/OR/比较表达式）</li>
 *   <li>归并为 MIN_UNIT（同表条件聚合、括号分组）</li>
 * </ol>
 */
package com.chenxy.demo.sql.parser;
