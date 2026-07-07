package com.chenxy.demo.sql.parser;

import com.chenxy.demo.sql.meta.TableMetaRegistry;
import com.chenxy.demo.sql.model.ComparisonNode;
import com.chenxy.demo.sql.model.ComparisonOperator;
import com.chenxy.demo.sql.model.ConditionNode;
import com.chenxy.demo.sql.model.OperandType;
import com.chenxy.demo.sql.model.TableInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 条件表达式解析器
 */
public class ConditionParser {

    private final TableMetaRegistry registry;
    private final ExpressionSanitizer sanitizer;
    private final String etlMonthColumn;
    private List<Token> tokens;
    private int pos;

    public ConditionParser(List<TableInfo> tableInfos) {
        this(new TableMetaRegistry(tableInfos), "etl_month");
    }

    public ConditionParser(TableMetaRegistry registry, String etlMonthColumn) {
        this.registry = registry;
        this.sanitizer = new ExpressionSanitizer();
        this.etlMonthColumn = etlMonthColumn;
    }

    public ConditionNode parse(String condition) {
        if (condition == null || condition.trim().isEmpty()) {
            throw new IllegalArgumentException("查询条件不能为空");
        }
        tokens = tokenize(condition);
        pos = 0;
        ConditionNode root = parseExpression();
        if (current().type != TokenType.EOF) {
            throw new IllegalArgumentException("条件语法错误，存在多余内容: " + current().text);
        }
        return groupMinimumUnits(root);
    }

    private ConditionNode parseExpression() {
        ConditionNode node = parseTerm();
        while (match(TokenType.OR)) {
            List<ConditionNode> children = new ArrayList<ConditionNode>();
            children.add(node);
            children.add(parseTerm());
            node = ConditionNode.or(children);
        }
        return node;
    }

    private ConditionNode parseTerm() {
        ConditionNode node = parseFactor();
        while (match(TokenType.AND)) {
            List<ConditionNode> children = new ArrayList<ConditionNode>();
            children.add(node);
            children.add(parseFactor());
            node = ConditionNode.and(children);
        }
        return node;
    }

    private ConditionNode parseFactor() {
        if (match(TokenType.LPAREN)) {
            ConditionNode inner = parseExpression();
            expect(TokenType.RPAREN, "缺少右括号");
            return inner;
        }
        return parsePredicate();
    }

    private ConditionNode parsePredicate() {
        if (isExpressionStart()) {
            String leftExpr = sanitizer.sanitizeExpression(scanExpression());
            return parseExpressionPredicate(leftExpr);
        }

        FieldRef leftField = parseFieldRef();
        registry.resolve(leftField.alias, leftField.column);

        if (matchKeyword("IS")) {
            ComparisonOperator op = matchKeyword("NOT")
                    ? ComparisonOperator.IS_NOT_NULL
                    : ComparisonOperator.IS_NULL;
            if (op == ComparisonOperator.IS_NULL) {
                expectKeyword("NULL");
            } else {
                expectKeyword("NULL");
            }
            ComparisonNode comparison = buildFieldComparison(leftField, op, null);
            return wrapLocalComparison(comparison);
        }

        if (matchKeyword("NOT")) {
            if (matchKeyword("LIKE")) {
                String value = parseLiteralOrExpression();
                ComparisonNode comparison = buildFieldComparison(leftField, ComparisonOperator.NOT_LIKE, value);
                comparison.setRightOperandType(isExpressionToken(value) ? OperandType.EXPRESSION : OperandType.LITERAL);
                if (comparison.getRightOperandType() == OperandType.EXPRESSION) {
                    comparison.setRightExpression(value);
                }
                return wrapLocalComparison(comparison);
            }
            if (matchKeyword("IN")) {
                expect(TokenType.LPAREN, "IN 操作符缺少左括号");
                String values = parseInValues();
                expect(TokenType.RPAREN, "IN 操作符缺少右括号");
                ComparisonNode comparison = buildFieldComparison(leftField, ComparisonOperator.NOT_IN, values);
                comparison.setInValues(true);
                return wrapLocalComparison(comparison);
            }
            throw new IllegalArgumentException("NOT 后应为 LIKE 或 IN，实际为: " + current().text);
        }

        if (matchKeyword("LIKE")) {
            String value = parseLiteralOrExpression();
            ComparisonNode comparison = buildFieldComparison(leftField, ComparisonOperator.LIKE, value);
            applyRightOperand(comparison, value);
            return wrapLocalComparison(comparison);
        }

        if (matchKeyword("IN")) {
            expect(TokenType.LPAREN, "IN 操作符缺少左括号");
            if (current().type == TokenType.IDENT && "SELECT".equalsIgnoreCase(current().text)) {
                String subquery = scanSelectSubquery();
                expect(TokenType.RPAREN, "子查询缺少右括号");
                ComparisonNode comparison = buildFieldComparison(leftField, ComparisonOperator.IN, subquery);
                comparison.setRightOperandType(OperandType.EXPRESSION);
                comparison.setRightExpression(subquery);
                comparison.setInValues(true);
                return wrapLocalComparison(comparison);
            }
            String values = parseInValues();
            expect(TokenType.RPAREN, "IN 操作符缺少右括号");
            ComparisonNode comparison = buildFieldComparison(leftField, ComparisonOperator.IN, values);
            comparison.setInValues(true);
            return wrapLocalComparison(comparison);
        }

        if (matchKeyword("BETWEEN")) {
            String lower = parseLiteralOrExpression();
            if (!match(TokenType.AND)) {
                throw new IllegalArgumentException("BETWEEN 缺少 AND 关键字");
            }
            String upper = parseLiteralOrExpression();
            ComparisonNode comparison = buildFieldComparison(leftField, ComparisonOperator.BETWEEN, lower);
            comparison.setBetweenUpper(upper);
            return wrapLocalComparison(comparison);
        }

        ComparisonOperator operator = parseOperator();
        return parseBinaryPredicate(leftField, operator);
    }

    private ConditionNode parseExpressionPredicate(String leftExpr) {
        if (matchKeyword("IS")) {
            ComparisonOperator op = matchKeyword("NOT")
                    ? ComparisonOperator.IS_NOT_NULL
                    : ComparisonOperator.IS_NULL;
            expectKeyword("NULL");
            return ConditionNode.raw(leftExpr + " " + op.getSymbol());
        }
        if (matchKeyword("NOT")) {
            if (matchKeyword("LIKE")) {
                String rhs = parseLiteralOrExpression();
                return ConditionNode.raw(leftExpr + " NOT LIKE " + rhs);
            }
            if (matchKeyword("IN")) {
                expect(TokenType.LPAREN, "IN 缺少左括号");
                String content;
                if (current().type == TokenType.IDENT && "SELECT".equalsIgnoreCase(current().text)) {
                    content = scanSelectSubquery();
                } else {
                    content = parseInValues();
                }
                expect(TokenType.RPAREN, "IN 缺少右括号");
                return ConditionNode.raw(leftExpr + " NOT IN (" + content + ")");
            }
        }
        if (matchKeyword("LIKE")) {
            String rhs = parseLiteralOrExpression();
            return ConditionNode.raw(leftExpr + " LIKE " + rhs);
        }
        if (matchKeyword("IN")) {
            expect(TokenType.LPAREN, "IN 缺少左括号");
            String content;
            if (current().type == TokenType.IDENT && "SELECT".equalsIgnoreCase(current().text)) {
                content = scanSelectSubquery();
            } else {
                content = parseInValues();
            }
            expect(TokenType.RPAREN, "IN 缺少右括号");
            return ConditionNode.raw(leftExpr + " IN (" + content + ")");
        }
        if (matchKeyword("BETWEEN")) {
            String lower = parseLiteralOrExpression();
            if (!match(TokenType.AND)) {
                throw new IllegalArgumentException("BETWEEN 缺少 AND 关键字");
            }
            String upper = parseLiteralOrExpression();
            return ConditionNode.raw(leftExpr + " BETWEEN " + lower + " AND " + upper);
        }
        ComparisonOperator operator = parseOperator();
        RightOperand rhs = parseRightOperand();
        if (rhs.type == OperandType.FIELD) {
            registry.resolve(rhs.field.alias, rhs.field.column);
            ComparisonNode comparison = new ComparisonNode();
            comparison.setLeftExpression(leftExpr);
            comparison.setOperator(operator);
            comparison.setRightOperandType(OperandType.FIELD);
            comparison.setRightTableAlias(rhs.field.alias);
            comparison.setRightColumn(rhs.field.column);
            fillCrossTableLeftRef(comparison, leftExpr);
            return ConditionNode.crossTable(comparison);
        }
        String rhsSql = rhs.expression != null ? rhs.expression : rhs.literal;
        return ConditionNode.raw(leftExpr + " " + operator.getSymbol() + " " + rhsSql);
    }

    private ConditionNode parseBinaryPredicate(FieldRef leftField, ComparisonOperator operator) {
        RightOperand rhs = parseRightOperand();
        if (rhs.type == OperandType.FIELD) {
            registry.resolve(rhs.field.alias, rhs.field.column);
            ComparisonNode comparison = buildFieldComparison(leftField, operator, null);
            comparison.setRightOperandType(OperandType.FIELD);
            comparison.setRightTableAlias(rhs.field.alias);
            comparison.setRightColumn(rhs.field.column);
            return ConditionNode.crossTable(comparison);
        }
        ComparisonNode comparison = buildFieldComparison(leftField, operator, rhs.literal);
        if (rhs.type == OperandType.EXPRESSION) {
            comparison.setRightOperandType(OperandType.EXPRESSION);
            comparison.setRightExpression(rhs.expression);
        }
        return wrapLocalComparison(comparison);
    }

    private void fillCrossTableLeftRef(ComparisonNode comparison, String leftExpr) {
        FieldRef ref = extractSingleFieldRef(leftExpr);
        if (ref != null) {
            registry.resolve(ref.alias, ref.column);
            comparison.setTableAlias(ref.alias);
            comparison.setColumn(ref.column);
        }
    }

    private FieldRef extractSingleFieldRef(String expression) {
        String trimmed = expression.trim();
        int dot = trimmed.indexOf('.');
        if (dot <= 0) {
            return null;
        }
        String alias = trimmed.substring(0, dot).trim();
        String column = trimmed.substring(dot + 1).trim();
        if (alias.isEmpty() || column.isEmpty() || column.contains(" ") || column.contains("(")) {
            return null;
        }
        return new FieldRef(alias, column);
    }

    private ConditionNode wrapLocalComparison(ComparisonNode comparison) {
        if (comparison.isExpressionPredicate()) {
            return ConditionNode.raw(comparison.toSqlFragment(comparison.getTableAlias()));
        }
        return ConditionNode.comparison(comparison);
    }

    private ComparisonNode buildFieldComparison(FieldRef field, ComparisonOperator operator, String value) {
        TableInfo info = registry.resolve(field.alias, field.column);
        ComparisonNode comparison = new ComparisonNode(info.getAlias(), field.column, operator, value);
        return comparison;
    }

    private void applyRightOperand(ComparisonNode comparison, String raw) {
        if (isExpressionToken(raw)) {
            comparison.setRightOperandType(OperandType.EXPRESSION);
            comparison.setRightExpression(raw);
        }
    }

    private ComparisonOperator parseOperator() {
        Token token = current();
        if (token.type == TokenType.OPERATOR) {
            pos++;
            return ComparisonOperator.fromSymbol(token.text);
        }
        throw new IllegalArgumentException("期望操作符，实际为: " + token.text);
    }

    private RightOperand parseRightOperand() {
        if (current().type == TokenType.IDENT && isFieldRefAhead()) {
            FieldRef field = parseFieldRef();
            return RightOperand.field(field);
        }
        if (isExpressionStart()) {
            String expr = sanitizer.sanitizeExpression(scanExpression());
            if (expr.regionMatches(true, 0, "SELECT", 0, 6)) {
                sanitizer.sanitizeSubquery(expr);
            }
            return RightOperand.expression(expr);
        }
        return RightOperand.literal(parseLiteralOrExpression());
    }

    private String parseLiteralOrExpression() {
        if (isExpressionStart()) {
            return sanitizer.sanitizeExpression(scanExpression());
        }
        return parseValueToken();
    }

    private String parseInValues() {
        StringBuilder sb = new StringBuilder();
        sb.append(parseLiteralOrExpression());
        while (match(TokenType.COMMA)) {
            sb.append(", ").append(parseLiteralOrExpression());
        }
        return sb.toString();
    }

    private String parseValueToken() {
        Token token = current();
        if (token.type == TokenType.STRING || token.type == TokenType.NUMBER || token.type == TokenType.IDENT) {
            pos++;
            return token.text;
        }
        throw new IllegalArgumentException("期望值，实际为: " + token.text);
    }

    private FieldRef parseFieldRef() {
        Token first = expect(TokenType.IDENT, "期望字段名");
        if (match(TokenType.DOT)) {
            Token second = expect(TokenType.IDENT, "期望字段名");
            return new FieldRef(first.text, second.text);
        }
        return new FieldRef(null, first.text);
    }

    private boolean isFieldRefAhead() {
        if (current().type != TokenType.IDENT) {
            return false;
        }
        if (pos + 1 >= tokens.size()) {
            return false;
        }
        if (tokens.get(pos + 1).type == TokenType.DOT) {
            if (pos + 2 >= tokens.size()) {
                return false;
            }
            Token afterColumn = tokens.get(pos + 3);
            return afterColumn.type != TokenType.LPAREN;
        }
        return tokens.get(pos + 1).type != TokenType.LPAREN;
    }

    private boolean isExpressionStart() {
        Token token = current();
        if (token.type == TokenType.LPAREN) {
            return true;
        }
        if (token.type == TokenType.IDENT && pos + 1 < tokens.size()
                && tokens.get(pos + 1).type == TokenType.LPAREN) {
            return true;
        }
        return false;
    }

    private String scanSelectSubquery() {
        StringBuilder sb = new StringBuilder();
        int parenDepth = 0;
        while (pos < tokens.size()) {
            Token token = current();
            if (token.type == TokenType.EOF) {
                throw new IllegalArgumentException("子查询未闭合");
            }
            if (token.type == TokenType.LPAREN) {
                parenDepth++;
            }
            if (token.type == TokenType.RPAREN) {
                if (parenDepth == 0) {
                    break;
                }
                parenDepth--;
            }
            appendTokenText(sb, token);
            pos++;
        }
        return sanitizer.sanitizeSubquery(sb.toString());
    }

    private void appendTokenText(StringBuilder sb, Token token) {
        if (token.type == TokenType.DOT) {
            sb.append(".");
            return;
        }
        if (sb.length() > 0 && !endsWithOpenDelimiter(sb)) {
            sb.append(" ");
        }
        sb.append(token.text);
    }

    private boolean endsWithOpenDelimiter(StringBuilder sb) {
        if (sb.length() == 0) {
            return true;
        }
        char last = sb.charAt(sb.length() - 1);
        return last == '(' || last == ',' || last == '.';
    }

    private String scanExpression() {
        if (current().type == TokenType.LPAREN) {
            return scanBalancedContent('(', ')');
        }
        return scanFunctionCall();
    }

    private String scanFunctionCall() {
        StringBuilder sb = new StringBuilder();
        Token ident = expect(TokenType.IDENT, "期望函数名或表达式");
        sb.append(ident.text);
        if (match(TokenType.LPAREN)) {
            sb.append("(").append(scanFunctionArguments()).append(")");
        }
        return sanitizer.sanitizeExpression(sb.toString());
    }

    private String scanFunctionArguments() {
        StringBuilder sb = new StringBuilder();
        int depth = 0;
        while (pos < tokens.size()) {
            Token token = current();
            if (token.type == TokenType.EOF) {
                break;
            }
            if (token.type == TokenType.LPAREN) {
                depth++;
            }
            if (token.type == TokenType.RPAREN) {
                if (depth == 0) {
                    pos++;
                    break;
                }
                depth--;
            }
            appendTokenText(sb, token);
            pos++;
        }
        return sb.toString().trim();
    }

    private String scanBalancedContent(char openChar, char closeChar) {
        expect(TokenType.LPAREN, "缺少左括号");
        StringBuilder sb = new StringBuilder("(");
        int depth = 1;
        while (pos < tokens.size() && depth > 0) {
            Token token = current();
            if (token.type == TokenType.EOF) {
                throw new IllegalArgumentException("括号未闭合");
            }
            if ("(".equals(token.text)) {
                depth++;
            } else if (")".equals(token.text)) {
                depth--;
                if (depth == 0) {
                    sb.append(")");
                    pos++;
                    break;
                }
            }
            if (depth > 0) {
                appendTokenText(sb, token);
            }
            if (depth > 0) {
                pos++;
            }
        }
        return sb.toString();
    }

    private boolean isExpressionToken(String token) {
        if (token == null) {
            return false;
        }
        String trimmed = token.trim();
        return trimmed.contains("(") || trimmed.regionMatches(true, 0, "SELECT", 0, 6);
    }

    private ConditionNode groupMinimumUnits(ConditionNode node) {
        if (node.getType() == ConditionNode.NodeType.AND) {
            return mergeAndChildren(node.flattenSameType());
        }
        if (node.getType() == ConditionNode.NodeType.OR) {
            List<ConditionNode> children = new ArrayList<ConditionNode>();
            for (ConditionNode child : node.getChildren()) {
                children.add(groupMinimumUnits(child));
            }
            return ConditionNode.or(children);
        }
        if (node.getType() == ConditionNode.NodeType.COMPARISON) {
            return wrapSingleComparison(node.getComparison());
        }
        return node;
    }

    private ConditionNode mergeAndChildren(List<ConditionNode> children) {
        Map<String, List<ComparisonNode>> tableComparisons = new LinkedHashMap<String, List<ComparisonNode>>();
        Map<String, String> aliasTableNames = new LinkedHashMap<String, String>();
        List<ConditionNode> others = new ArrayList<ConditionNode>();

        for (ConditionNode child : children) {
            if (child.getType() == ConditionNode.NodeType.MIN_UNIT) {
                mergeMinUnit(tableComparisons, aliasTableNames, child);
            } else if (child.getType() == ConditionNode.NodeType.COMPARISON) {
                ComparisonNode comparison = child.getComparison();
                TableInfo info = registry.resolve(comparison.getTableAlias(), comparison.getColumn());
                String alias = info.getAlias();
                comparison.setTableAlias(alias);
                if (!tableComparisons.containsKey(alias)) {
                    tableComparisons.put(alias, new ArrayList<ComparisonNode>());
                }
                tableComparisons.get(alias).add(comparison);
                aliasTableNames.put(alias, info.getTableName());
            } else if (child.getType() == ConditionNode.NodeType.CROSS_TABLE
                    || child.getType() == ConditionNode.NodeType.RAW) {
                others.add(child);
            } else if (child.getType() == ConditionNode.NodeType.OR) {
                others.add(groupMinimumUnits(child));
            } else if (child.getType() == ConditionNode.NodeType.AND) {
                ConditionNode mergedChild = mergeAndChildren(child.flattenSameType());
                if (mergedChild.getType() == ConditionNode.NodeType.AND) {
                    for (ConditionNode grand : mergedChild.getChildren()) {
                        distributeAndChild(grand, tableComparisons, aliasTableNames, others);
                    }
                } else {
                    distributeAndChild(mergedChild, tableComparisons, aliasTableNames, others);
                }
            } else {
                others.add(child);
            }
        }

        List<ConditionNode> merged = new ArrayList<ConditionNode>(others);
        for (Map.Entry<String, List<ComparisonNode>> entry : tableComparisons.entrySet()) {
            merged.add(buildMinUnit(entry.getKey(), aliasTableNames.get(entry.getKey()), entry.getValue()));
        }
        if (merged.size() == 1) {
            return merged.get(0);
        }
        return ConditionNode.and(merged);
    }

    private void distributeAndChild(ConditionNode child,
                                    Map<String, List<ComparisonNode>> tableComparisons,
                                    Map<String, String> aliasTableNames,
                                    List<ConditionNode> others) {
        if (child.getType() == ConditionNode.NodeType.MIN_UNIT) {
            mergeMinUnit(tableComparisons, aliasTableNames, child);
        } else if (child.getType() == ConditionNode.NodeType.COMPARISON) {
            ComparisonNode comparison = child.getComparison();
            TableInfo info = registry.resolve(comparison.getTableAlias(), comparison.getColumn());
            comparison.setTableAlias(info.getAlias());
            if (!tableComparisons.containsKey(info.getAlias())) {
                tableComparisons.put(info.getAlias(), new ArrayList<ComparisonNode>());
            }
            tableComparisons.get(info.getAlias()).add(comparison);
            aliasTableNames.put(info.getAlias(), info.getTableName());
        } else {
            others.add(child);
        }
    }

    private void mergeMinUnit(Map<String, List<ComparisonNode>> tableComparisons,
                              Map<String, String> aliasTableNames,
                              ConditionNode child) {
        String alias = child.getTableAlias();
        if (!tableComparisons.containsKey(alias)) {
            tableComparisons.put(alias, new ArrayList<ComparisonNode>());
        }
        tableComparisons.get(alias).addAll(child.getComparisons());
        aliasTableNames.put(alias, child.getTableName());
    }

    private ConditionNode wrapSingleComparison(ComparisonNode comparison) {
        if (comparison.isCrossTable()) {
            return ConditionNode.crossTable(comparison);
        }
        TableInfo info = registry.resolve(comparison.getTableAlias(), comparison.getColumn());
        comparison.setTableAlias(info.getAlias());
        List<ComparisonNode> list = new ArrayList<ComparisonNode>();
        list.add(comparison);
        return buildMinUnit(info.getAlias(), info.getTableName(), list);
    }

    private ConditionNode buildMinUnit(String alias, String tableName, List<ComparisonNode> comparisons) {
        boolean hasEtlMonth = false;
        boolean hasBusiness = false;
        for (ComparisonNode comparison : comparisons) {
            if (etlMonthColumn.equalsIgnoreCase(comparison.getColumn())) {
                hasEtlMonth = true;
            } else {
                hasBusiness = true;
            }
        }
        if (!hasEtlMonth || !hasBusiness) {
            throw new IllegalArgumentException("最小查询条件必须同时包含 etl_month 和业务字段，表别名: " + alias);
        }
        return ConditionNode.minUnit(alias, tableName, comparisons);
    }

    private List<Token> tokenize(String input) {
        List<Token> list = new ArrayList<Token>();
        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (c == '(') {
                list.add(new Token(TokenType.LPAREN, "("));
                i++;
                continue;
            }
            if (c == ')') {
                list.add(new Token(TokenType.RPAREN, ")"));
                i++;
                continue;
            }
            if (c == ',') {
                list.add(new Token(TokenType.COMMA, ","));
                i++;
                continue;
            }
            if (c == '.') {
                list.add(new Token(TokenType.DOT, "."));
                i++;
                continue;
            }
            if (c == '\'' || c == '"') {
                char quote = c;
                i++;
                StringBuilder sb = new StringBuilder();
                sb.append(quote);
                while (i < input.length()) {
                    char ch = input.charAt(i);
                    sb.append(ch);
                    i++;
                    if (ch == quote) {
                        break;
                    }
                }
                list.add(new Token(TokenType.STRING, sb.toString()));
                continue;
            }
            if (c == '!' && i + 1 < input.length() && input.charAt(i + 1) == '=') {
                list.add(new Token(TokenType.OPERATOR, "!="));
                i += 2;
                continue;
            }
            if (c == '=' || c == '>' || c == '<') {
                if (i + 1 < input.length() && input.charAt(i + 1) == '=') {
                    list.add(new Token(TokenType.OPERATOR, input.substring(i, i + 2)));
                    i += 2;
                } else {
                    list.add(new Token(TokenType.OPERATOR, String.valueOf(c)));
                    i++;
                }
                continue;
            }
            if (Character.isDigit(c) || (c == '-' && i + 1 < input.length() && Character.isDigit(input.charAt(i + 1)))) {
                int start = i;
                i++;
                while (i < input.length() && (Character.isDigit(input.charAt(i)) || input.charAt(i) == '.')) {
                    i++;
                }
                list.add(new Token(TokenType.NUMBER, input.substring(start, i)));
                continue;
            }
            if (Character.isLetter(c) || c == '_') {
                int start = i;
                i++;
                while (i < input.length()) {
                    char ch = input.charAt(i);
                    if (Character.isLetterOrDigit(ch) || ch == '_') {
                        i++;
                    } else {
                        break;
                    }
                }
                String word = input.substring(start, i);
                if ("AND".equalsIgnoreCase(word)) {
                    list.add(new Token(TokenType.AND, word));
                } else if ("OR".equalsIgnoreCase(word)) {
                    list.add(new Token(TokenType.OR, word));
                } else {
                    list.add(new Token(TokenType.IDENT, word));
                }
                continue;
            }
            throw new IllegalArgumentException("无法识别的字符: " + c);
        }
        list.add(new Token(TokenType.EOF, ""));
        return list;
    }

    private boolean match(TokenType type) {
        if (current().type == type) {
            pos++;
            return true;
        }
        return false;
    }

    private boolean matchKeyword(String keyword) {
        if (current().type == TokenType.IDENT && keyword.equalsIgnoreCase(current().text)) {
            pos++;
            return true;
        }
        return false;
    }

    private void expectKeyword(String keyword) {
        if (!matchKeyword(keyword)) {
            throw new IllegalArgumentException("期望关键字 " + keyword + "，实际为: " + current().text);
        }
    }

    private Token expect(TokenType type, String message) {
        Token token = current();
        if (token.type != type) {
            throw new IllegalArgumentException(message + "，实际为: " + token.text);
        }
        pos++;
        return token;
    }

    private Token current() {
        return tokens.get(pos);
    }

    private static class FieldRef {
        private final String alias;
        private final String column;

        private FieldRef(String alias, String column) {
            this.alias = alias;
            this.column = column;
        }
    }

    private static class RightOperand {
        private final OperandType type;
        private final FieldRef field;
        private final String literal;
        private final String expression;

        private RightOperand(OperandType type, FieldRef field, String literal, String expression) {
            this.type = type;
            this.field = field;
            this.literal = literal;
            this.expression = expression;
        }

        static RightOperand field(FieldRef field) {
            return new RightOperand(OperandType.FIELD, field, null, null);
        }

        static RightOperand literal(String literal) {
            return new RightOperand(OperandType.LITERAL, null, literal, null);
        }

        static RightOperand expression(String expression) {
            return new RightOperand(OperandType.EXPRESSION, null, null, expression);
        }
    }

    private enum TokenType {
        IDENT, STRING, NUMBER, OPERATOR, AND, OR, LPAREN, RPAREN, DOT, COMMA, EOF
    }

    private static class Token {
        private final TokenType type;
        private final String text;

        private Token(TokenType type, String text) {
            this.type = type;
            this.text = text;
        }
    }
}
