package com.chenxy.demo.sql.validator;

import com.chenxy.demo.sql.model.ComparisonNode;
import com.chenxy.demo.sql.model.OperandType;

/**
 * packageName com.scredit.crs.sql.validator
 *
 * @author chenxy
 * @className ComparisonValueUtils
 * @date 17 7月 2026 10:22
 * @Version 1.0.0
 * @description TODO
 */
public class ComparisonValueUtils {
    private ComparisonValueUtils() {
    }

    static int compareValues(String left, String right) {
        String leftText = stripQuote(left);
        String rightText = stripQuote(right);
        Double leftNumber = tryParseNumber(leftText);
        Double rightNumber = tryParseNumber(rightText);
        if (leftNumber != null && rightNumber != null) {
            return leftNumber.compareTo(rightNumber);
        }
        return leftText.compareTo(rightText);
    }

    static boolean isValidClosedRange(String lower, String upper) {
        return compareValues(lower, upper) <= 0;
    }

    static boolean isValidOpenEndedRange(String lower, boolean lowerInclusive,
                                         String upper, boolean upperInclusive) {
        int cmp = compareValues(lower, upper);
        if (cmp < 0) {
            return true;
        }
        if (cmp > 0) {
            return false;
        }
        return lowerInclusive && upperInclusive;
    }

    static String stripQuote(String value) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        if ((text.startsWith("'") && text.endsWith("'")) || (text.startsWith("\"") && text.endsWith("\""))) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

    static Double tryParseNumber(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    static boolean hasLiteralRhs(ComparisonNode comparison) {
        return comparison == null || comparison.getRightOperandType() != OperandType.EXPRESSION;
    }
}