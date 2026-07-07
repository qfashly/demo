package com.chenxy.demo.sql.parser;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 表达式安全校验，防止注入
 */
public class ExpressionSanitizer {

    private static final Pattern DANGEROUS = Pattern.compile(
            "(--|;|/\\*|\\*/|\\b(DROP|DELETE|UPDATE|INSERT|ALTER|TRUNCATE|EXEC|EXECUTE|UNION\\s+SELECT)\\b)",
            Pattern.CASE_INSENSITIVE);

    private static final Set<String> ALLOWED_FUNCTIONS = new HashSet<String>(Arrays.asList(
            "DATE", "DATETIME", "YEAR", "MONTH", "DAY", "UPPER", "LOWER", "TRIM", "LENGTH",
            "SUBSTRING", "CONCAT", "IFNULL", "COALESCE", "ABS", "ROUND", "CAST", "CONVERT",
            "LEFT", "RIGHT", "REPLACE", "NOW", "CURDATE", "STR_TO_DATE", "DATE_FORMAT"
    ));

    public String sanitizeExpression(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            throw new IllegalArgumentException("表达式不能为空");
        }
        String trimmed = expression.trim();
        if (DANGEROUS.matcher(trimmed).find()) {
            throw new IllegalArgumentException("表达式包含不允许的 SQL 关键字: " + trimmed);
        }
        validateFunctions(trimmed);
        return trimmed;
    }

    public String sanitizeSubquery(String subquery) {
        String sanitized = sanitizeExpression(subquery);
        if (!sanitized.regionMatches(true, 0, "SELECT", 0, 6)) {
            throw new IllegalArgumentException("子查询必须以 SELECT 开头");
        }
        return sanitized;
    }

    private void validateFunctions(String expression) {
        String upper = expression.toUpperCase(Locale.ROOT);
        int idx = 0;
        while (idx < upper.length()) {
            if (Character.isLetter(upper.charAt(idx)) || upper.charAt(idx) == '_') {
                int start = idx;
                while (idx < upper.length() && (Character.isLetterOrDigit(upper.charAt(idx)) || upper.charAt(idx) == '_')) {
                    idx++;
                }
                if (idx < upper.length() && upper.charAt(idx) == '(') {
                    String func = upper.substring(start, idx);
                    if (!ALLOWED_FUNCTIONS.contains(func)) {
                        throw new IllegalArgumentException("不支持的函数: " + func);
                    }
                }
            } else {
                idx++;
            }
        }
    }
}
