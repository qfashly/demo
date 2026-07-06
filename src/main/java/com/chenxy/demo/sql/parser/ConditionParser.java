package com.chenxy.demo.sql.parser;

import com.chenxy.demo.sql.model.ComparisonNode;
import com.chenxy.demo.sql.model.ComparisonOperator;
import com.chenxy.demo.sql.model.ConditionNode;
import com.chenxy.demo.sql.model.TableInfo;
import com.chenxy.demo.sql.meta.TableMetaRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 条件表达式解析器
 */
public class ConditionParser {

    private final TableMetaRegistry registry;
    private final String etlMonthColumn;
    private List<Token> tokens;
    private int pos;

    public ConditionParser(List<TableInfo> tableInfos) {
        this(new TableMetaRegistry(tableInfos), "etl_month");
    }

    public ConditionParser(TableMetaRegistry registry, String etlMonthColumn) {
        this.registry = registry;
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
        return ConditionNode.comparison(parseComparison());
    }

    private ComparisonNode parseComparison() {
        FieldRef fieldRef = parseFieldRef();
        if (matchKeyword("NOT")) {
            expectKeyword("IN");
            expect(TokenType.LPAREN, "IN 操作符缺少左括号");
            String values = parseInValues();
            expect(TokenType.RPAREN, "IN 操作符缺少右括号");
            ComparisonNode node = new ComparisonNode(fieldRef.alias, fieldRef.column, ComparisonOperator.NE, values);
            node.setInValues(true);
            registry.resolve(fieldRef.alias, fieldRef.column);
            return node;
        }
        if (matchKeyword("IN")) {
            expect(TokenType.LPAREN, "IN 操作符缺少左括号");
            String values = parseInValues();
            expect(TokenType.RPAREN, "IN 操作符缺少右括号");
            ComparisonNode node = new ComparisonNode(fieldRef.alias, fieldRef.column, ComparisonOperator.IN, values);
            node.setInValues(true);
            registry.resolve(fieldRef.alias, fieldRef.column);
            return node;
        }
        if (matchKeyword("LIKE")) {
            String value = parseValueToken();
            ComparisonNode node = new ComparisonNode(fieldRef.alias, fieldRef.column, ComparisonOperator.LIKE, value);
            registry.resolve(fieldRef.alias, fieldRef.column);
            return node;
        }
        ComparisonOperator operator = parseOperator();
        String value = parseValueToken();
        ComparisonNode node = new ComparisonNode(fieldRef.alias, fieldRef.column, operator, value);
        registry.resolve(fieldRef.alias, fieldRef.column);
        return node;
    }

    private ComparisonOperator parseOperator() {
        Token token = current();
        if (token.type == TokenType.OPERATOR) {
            pos++;
            return ComparisonOperator.fromSymbol(token.text);
        }
        throw new IllegalArgumentException("期望操作符，实际为: " + token.text);
    }

    private String parseInValues() {
        StringBuilder sb = new StringBuilder();
        sb.append(parseValueToken());
        while (match(TokenType.COMMA)) {
            sb.append(", ").append(parseValueToken());
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
                String alias = child.getTableAlias();
                if (!tableComparisons.containsKey(alias)) {
                    tableComparisons.put(alias, new ArrayList<ComparisonNode>());
                }
                tableComparisons.get(alias).addAll(child.getComparisons());
                aliasTableNames.put(alias, child.getTableName());
            } else if (child.getType() == ConditionNode.NodeType.COMPARISON) {
                ComparisonNode comparison = child.getComparison();
                TableInfo info = registry.resolve(comparison.getTableAlias(), comparison.getColumn());
                String alias = info.getAlias();
                if (!tableComparisons.containsKey(alias)) {
                    tableComparisons.put(alias, new ArrayList<ComparisonNode>());
                }
                comparison.setTableAlias(alias);
                tableComparisons.get(alias).add(comparison);
                aliasTableNames.put(alias, info.getTableName());
            } else if (child.getType() == ConditionNode.NodeType.OR) {
                others.add(groupMinimumUnits(child));
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

    private ConditionNode wrapSingleComparison(ComparisonNode comparison) {
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
            if (Character.isDigit(c) || c == '-') {
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
