package com.chenxy.demo.sql.builder;

import com.chenxy.demo.sql.meta.TableMetaRegistry;
import com.chenxy.demo.sql.model.ComparisonNode;
import com.chenxy.demo.sql.model.ConditionNode;
import com.chenxy.demo.sql.model.SqlQueryConfig;
import com.chenxy.demo.sql.model.TableInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SQL 组装器
 */
public class SqlQueryBuilder {

    private final TableMetaRegistry registry;
    private final SqlQueryConfig config;
    private int joinIndex = 1;

    public SqlQueryBuilder(List<TableInfo> tableInfos, SqlQueryConfig config) {
        this.registry = new TableMetaRegistry(tableInfos);
        this.config = config == null ? new SqlQueryConfig() : config;
    }

    public SqlQueryBuilder(TableMetaRegistry registry, SqlQueryConfig config) {
        this.registry = registry;
        this.config = config == null ? new SqlQueryConfig() : config;
    }

    public String buildCountSql(ConditionNode condition) {
        return buildSql(condition, true);
    }

    public String buildQuerySql(ConditionNode condition) {
        return buildSql(condition, false);
    }

    private String buildSql(ConditionNode condition, boolean countMode) {
        joinIndex = 1;
        BuildContext ctx = new BuildContext();
        List<JoinUnit> joinUnits = buildJoinUnits(condition, ctx);
        List<String> postJoinPredicates = collectPostJoinPredicates(condition, ctx.getAliasJoinMap());

        StringBuilder sql = new StringBuilder();
        if (countMode) {
            sql.append("select count(1) as cnt\n");
        } else {
            sql.append("select ").append(buildSelectColumns(joinUnits)).append("\n");
        }
        sql.append("from \n");
        sql.append("(\n");
        sql.append("\tselect cid, ent_name, uni_scid\n");
        sql.append("\tfrom ").append(config.qualifiedMainTable()).append(" \n");
        sql.append(") ").append(config.getMainTableAlias()).append("\n");

        for (JoinUnit joinUnit : joinUnits) {
            sql.append("join \n");
            sql.append("(\n");
            sql.append(joinUnit.subquery);
            sql.append(") ").append(joinUnit.joinAlias).append(" on ")
                    .append(config.getMainTableAlias()).append(".")
                    .append(config.getJoinKeyColumn()).append(" = ")
                    .append(joinUnit.joinAlias).append(".")
                    .append(config.getJoinKeyColumn()).append("\n");
        }

        if (!postJoinPredicates.isEmpty()) {
            sql.append("where ").append(joinWithAnd(postJoinPredicates)).append("\n");
        }
        return sql.toString().trim();
    }

    private String buildSelectColumns(List<JoinUnit> joinUnits) {
        List<String> columns = new ArrayList<String>();
        columns.add(selectWithAlias(config.getMainTableAlias() + ".cid", "cid"));
        columns.add(selectWithAlias(config.getMainTableAlias() + ".ent_name", "ent_name"));
        columns.add(selectWithAlias(config.getMainTableAlias() + ".uni_scid", "uni_scid"));
        for (JoinUnit joinUnit : joinUnits) {
            columns.addAll(joinUnit.selectColumns);
        }
        return joinColumns(columns);
    }

    private String selectWithAlias(String expression, String alias) {
        return expression + " as " + alias;
    }

    private String joinColumns(List<String> columns) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(columns.get(i));
        }
        return sb.toString();
    }

    private List<JoinUnit> buildJoinUnits(ConditionNode node, BuildContext ctx) {
        List<JoinUnit> units = new ArrayList<JoinUnit>();
        if (node.getType() == ConditionNode.NodeType.AND) {
            for (ConditionNode child : node.getChildren()) {
                units.addAll(buildJoinUnits(child, ctx));
            }
            return units;
        }
        if (node.getType() == ConditionNode.NodeType.OR) {
            units.add(buildUnionJoinUnit(node, ctx));
            return units;
        }
        if (node.getType() == ConditionNode.NodeType.MIN_UNIT) {
            units.add(buildSingleTableJoinUnit(node, ctx));
            return units;
        }
        if (node.getType() == ConditionNode.NodeType.CROSS_TABLE || node.getType() == ConditionNode.NodeType.RAW) {
            return units;
        }
        throw new IllegalArgumentException("不支持的条件节点类型: " + node.getType());
    }

    private List<String> collectPostJoinPredicates(ConditionNode node, Map<String, String> aliasJoinMap) {
        List<String> predicates = new ArrayList<String>();
        collectPostJoinPredicatesInternal(node, aliasJoinMap, predicates);
        return predicates;
    }

    private void collectPostJoinPredicatesInternal(ConditionNode node,
                                                   Map<String, String> aliasJoinMap,
                                                   List<String> predicates) {
        if (node.getType() == ConditionNode.NodeType.CROSS_TABLE) {
            ComparisonNode comparison = node.getComparison();
            String leftJoin = resolveJoinAlias(aliasJoinMap, comparison.getTableAlias(), comparison.getColumn());
            String rightJoin = resolveJoinAlias(aliasJoinMap, comparison.getRightTableAlias(), comparison.getRightColumn());
            if (leftJoin == null || rightJoin == null) {
                throw new IllegalArgumentException("跨表条件引用了未参与 JOIN 的表: "
                        + comparison.getTableAlias() + "." + comparison.getColumn());
            }
            predicates.add(renderCrossTablePredicate(comparison, aliasJoinMap));
            return;
        }
        if (node.getType() == ConditionNode.NodeType.RAW) {
            predicates.add(renderRawSql(node.getRawSql(), aliasJoinMap));
            return;
        }
        if (node.getType() == ConditionNode.NodeType.AND) {
            for (ConditionNode child : node.getChildren()) {
                collectPostJoinPredicatesInternal(child, aliasJoinMap, predicates);
            }
        }
    }

    private JoinUnit buildSingleTableJoinUnit(ConditionNode minUnit, BuildContext ctx) {
        String alias = minUnit.getTableAlias();
        String tableName = resolveTableName(alias, minUnit.getTableName());
        String joinAlias = nextJoinAlias();
        ctx.registerMinUnit(minUnit, joinAlias);
        List<String> businessColumns = extractBusinessColumns(minUnit);

        StringBuilder subquery = new StringBuilder();
        subquery.append("\tselect ").append(alias).append(".cid");
        for (String column : businessColumns) {
            subquery.append(", ").append(alias).append(".").append(column);
        }
        subquery.append("\n");
        subquery.append("\tfrom ").append(tableName).append(" ").append(alias).append("\n");
        subquery.append("\twhere ").append(buildWhereClause(alias, minUnit.getComparisons())).append("\n");

        List<String> selectColumns = new ArrayList<String>();
        for (String column : businessColumns) {
            selectColumns.add(selectWithAlias(joinAlias + "." + column, column));
        }
        return new JoinUnit(joinAlias, subquery.toString(), selectColumns);
    }

    private JoinUnit buildUnionJoinUnit(ConditionNode orNode, BuildContext ctx) {
        String joinAlias = nextJoinAlias();
        List<ConditionNode> branches = orNode.getChildren();
        Set<String> allColumns = new LinkedHashSet<String>();
        List<BranchSql> branchSqlList = new ArrayList<BranchSql>();

        for (ConditionNode branch : branches) {
            BranchSql branchSql = buildBranchSql(branch);
            branchSqlList.add(branchSql);
            allColumns.addAll(branchSql.outputColumns);
        }

        StringBuilder subquery = new StringBuilder();
        for (int i = 0; i < branchSqlList.size(); i++) {
            BranchSql branchSql = branchSqlList.get(i);
            if (i > 0) {
                subquery.append("\n\t\n\tunion\n\t\n");
            }
            subquery.append("\tselect ").append(branchSql.cidColumn);
            for (String column : allColumns) {
                if (branchSql.outputColumns.contains(column)) {
                    subquery.append(", ").append(branchSql.columnSelectMap.get(column));
                } else {
                    subquery.append(", null as ").append(column);
                }
            }
            subquery.append("\n\tfrom (\n");
            subquery.append(branchSql.body);
            subquery.append("\n\t) ").append(branchSql.branchAlias).append("\n");
        }

        for (BranchSql branchSql : branchSqlList) {
            ctx.registerBranchAliases(branchSql.tableAliasJoinMap, joinAlias);
        }

        List<String> selectColumns = new ArrayList<String>();
        for (String column : allColumns) {
            selectColumns.add(selectWithAlias(joinAlias + "." + column, column));
        }
        return new JoinUnit(joinAlias, subquery.toString(), selectColumns);
    }

    private BranchSql buildBranchSql(ConditionNode branch) {
        if (branch.getType() == ConditionNode.NodeType.MIN_UNIT) {
            return buildMinUnitBranch(branch);
        }
        if (branch.getType() == ConditionNode.NodeType.AND) {
            return buildAndBranch(branch);
        }
        if (branch.getType() == ConditionNode.NodeType.OR) {
            return buildNestedOrBranch(branch);
        }
        throw new IllegalArgumentException("OR 分支不支持的条件类型: " + branch.getType());
    }

    private BranchSql buildMinUnitBranch(ConditionNode minUnit) {
        String alias = minUnit.getTableAlias();
        String tableName = resolveTableName(alias, minUnit.getTableName());
        String branchAlias = "br_" + alias;
        List<String> columns = registry.getBusinessColumns(alias);
        Map<String, String> columnSelectMap = new LinkedHashMap<String, String>();
        for (String column : columns) {
            columnSelectMap.put(column, branchAlias + "." + column);
        }

        StringBuilder body = new StringBuilder();
        body.append("\t\tselect ").append(alias).append(".cid as cid");
        for (String column : columns) {
            body.append(", ").append(alias).append(".").append(column);
        }
        body.append("\n");
        body.append("\t\tfrom ").append(tableName).append(" ").append(alias).append("\n");
        body.append("\t\twhere ").append(buildWhereClause(alias, minUnit.getComparisons())).append("\n");

        Map<String, String> tableAliasJoinMap = new LinkedHashMap<String, String>();
        tableAliasJoinMap.put(alias, branchAlias);
        return new BranchSql(branchAlias, body.toString(), branchAlias + ".cid", columns, columnSelectMap, tableAliasJoinMap);
    }

    private BranchSql buildAndBranch(ConditionNode andNode) {
        List<ConditionNode> minUnits = new ArrayList<ConditionNode>();
        List<ConditionNode> postNodes = new ArrayList<ConditionNode>();
        extractAndBranchParts(andNode, minUnits, postNodes);

        if (minUnits.isEmpty()) {
            throw new IllegalArgumentException("AND 分支至少包含一个单表最小条件单元");
        }

        Set<String> allColumns = new LinkedHashSet<String>();
        List<BranchSql> tableBranches = new ArrayList<BranchSql>();
        for (ConditionNode minUnit : minUnits) {
            BranchSql tableBranch = buildMinUnitBranch(minUnit);
            tableBranches.add(tableBranch);
            allColumns.addAll(tableBranch.outputColumns);
        }

        Map<String, String> branchAliasMap = new LinkedHashMap<String, String>();
        for (BranchSql tableBranch : tableBranches) {
            branchAliasMap.putAll(tableBranch.tableAliasJoinMap);
        }

        List<String> localPredicates = new ArrayList<String>();
        for (ConditionNode postNode : postNodes) {
            if (postNode.getType() == ConditionNode.NodeType.CROSS_TABLE) {
                localPredicates.add(renderCrossTablePredicate(postNode.getComparison(), branchAliasMap));
            } else if (postNode.getType() == ConditionNode.NodeType.RAW) {
                localPredicates.add(renderRawSql(postNode.getRawSql(), branchAliasMap));
            }
        }

        String firstAlias = tableBranches.get(0).branchAlias;
        StringBuilder body = new StringBuilder();
        body.append("\t\tselect ").append(firstAlias).append(".cid as cid");
        for (String column : allColumns) {
            String selectExpr = null;
            for (BranchSql tableBranch : tableBranches) {
                if (tableBranch.outputColumns.contains(column)) {
                    selectExpr = tableBranch.columnSelectMap.get(column);
                    break;
                }
            }
            body.append(", ").append(selectExpr);
        }
        body.append("\n");
        body.append("\t\tfrom (\n").append(tableBranches.get(0).body).append("\t\t) ").append(firstAlias);

        Map<String, String> tableAliasJoinMap = new LinkedHashMap<String, String>();
        tableAliasJoinMap.putAll(tableBranches.get(0).tableAliasJoinMap);

        for (int i = 1; i < tableBranches.size(); i++) {
            BranchSql next = tableBranches.get(i);
            body.append("\n");
            body.append("\t\tjoin (\n").append(next.body).append("\t\t) ")
                    .append(next.branchAlias).append(" on ")
                    .append(firstAlias).append(".cid = ")
                    .append(next.branchAlias).append(".cid");
            tableAliasJoinMap.putAll(next.tableAliasJoinMap);
        }

        if (!localPredicates.isEmpty()) {
            body.append("\n");
            body.append("\t\twhere ").append(joinWithAnd(localPredicates));
        }

        Map<String, String> columnSelectMap = new LinkedHashMap<String, String>();
        String branchAlias = "br_and";
        for (String column : allColumns) {
            columnSelectMap.put(column, branchAlias + "." + column);
        }

        String wrapped = body.toString();
        String finalBody = "\t\tselect " + branchAlias + ".cid as cid";
        for (String column : allColumns) {
            finalBody += ", " + branchAlias + "." + column;
        }
        finalBody += "\n\t\tfrom (\n" + wrapped + "\n\t\t) " + branchAlias + "\n";

        Map<String, String> mergedAliasMap = new LinkedHashMap<String, String>();
        for (String tableAlias : tableAliasJoinMap.keySet()) {
            mergedAliasMap.put(tableAlias, branchAlias);
        }

        return new BranchSql(branchAlias, finalBody, branchAlias + ".cid", new ArrayList<String>(allColumns),
                columnSelectMap, mergedAliasMap, localPredicates);
    }

    private void extractAndBranchParts(ConditionNode node,
                                       List<ConditionNode> minUnits,
                                       List<ConditionNode> postNodes) {
        if (node.getType() == ConditionNode.NodeType.MIN_UNIT) {
            minUnits.add(node);
            return;
        }
        if (node.getType() == ConditionNode.NodeType.AND) {
            for (ConditionNode child : node.getChildren()) {
                extractAndBranchParts(child, minUnits, postNodes);
            }
            return;
        }
        if (node.getType() == ConditionNode.NodeType.CROSS_TABLE || node.getType() == ConditionNode.NodeType.RAW) {
            postNodes.add(node);
            return;
        }
        throw new IllegalArgumentException("AND 分支内不支持节点类型: " + node.getType());
    }

    private BranchSql buildNestedOrBranch(ConditionNode orNode) {
        Set<String> allColumns = new LinkedHashSet<String>();
        List<BranchSql> branchSqlList = new ArrayList<BranchSql>();
        for (ConditionNode child : orNode.getChildren()) {
            BranchSql branchSql = buildBranchSql(child);
            branchSqlList.add(branchSql);
            allColumns.addAll(branchSql.outputColumns);
        }

        String branchAlias = "br_or";
        StringBuilder unionBody = new StringBuilder();
        for (int i = 0; i < branchSqlList.size(); i++) {
            BranchSql branchSql = branchSqlList.get(i);
            if (i > 0) {
                unionBody.append("\n\t\tunion\n");
            }
            unionBody.append("\t\tselect ").append(branchSql.cidColumn);
            for (String column : allColumns) {
                if (branchSql.outputColumns.contains(column)) {
                    unionBody.append(", ").append(branchSql.columnSelectMap.get(column));
                } else {
                    unionBody.append(", null as ").append(column);
                }
            }
            unionBody.append("\n\t\tfrom (\n").append(branchSql.body).append("\t\t) ")
                    .append(branchSql.branchAlias).append("\n");
        }

        Map<String, String> columnSelectMap = new LinkedHashMap<String, String>();
        for (String column : allColumns) {
            columnSelectMap.put(column, branchAlias + "." + column);
        }

        String body = "\t\tselect " + branchAlias + ".cid as cid";
        for (String column : allColumns) {
            body += ", " + branchAlias + "." + column;
        }
        body += "\n\t\tfrom (\n" + unionBody + "\t\t) " + branchAlias + "\n";

        Map<String, String> aliasMap = new LinkedHashMap<String, String>();
        aliasMap.put("nested_or", branchAlias);
        return new BranchSql(branchAlias, body, branchAlias + ".cid", new ArrayList<String>(allColumns),
                columnSelectMap, aliasMap);
    }

    private String renderCrossTablePredicate(ComparisonNode comparison, Map<String, String> aliasJoinMap) {
        String leftJoin = resolveJoinAlias(aliasJoinMap, comparison.getTableAlias(), comparison.getColumn());
        String rightJoin = resolveJoinAlias(aliasJoinMap, comparison.getRightTableAlias(), comparison.getRightColumn());
        if (comparison.getLeftExpression() != null && !comparison.getLeftExpression().isEmpty()) {
            String left = renderRawSql(comparison.getLeftExpression(), aliasJoinMap);
            String right = rightJoin + "." + comparison.getRightColumn();
            return left + " " + comparison.getOperator().getSymbol() + " " + right;
        }
        if (leftJoin == null || rightJoin == null) {
            return comparison.toCrossTableSqlFragment(
                    comparison.getTableAlias() + "." + comparison.getColumn(),
                    comparison.getRightTableAlias() + "." + comparison.getRightColumn());
        }
        return comparison.toCrossTableSqlFragment(leftJoin, rightJoin);
    }

    private String renderRawSql(String rawSql, Map<String, String> aliasJoinMap) {
        String rendered = rawSql;
        List<String> aliases = new ArrayList<String>(aliasJoinMap.keySet());
        java.util.Collections.sort(aliases, new java.util.Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return o2.length() - o1.length();
            }
        });
        for (String tableAlias : aliases) {
            String joinAlias = aliasJoinMap.get(tableAlias);
            rendered = rendered.replaceAll("\\b" + tableAlias + "\\.", joinAlias + ".");
        }
        return rendered;
    }

    private String resolveTableName(String alias, String tableName) {
        if (tableName != null && !tableName.trim().isEmpty()) {
            return tableName;
        }
        return registry.getTableName(alias);
    }

    private List<String> extractBusinessColumns(ConditionNode minUnit) {
        LinkedHashSet<String> columns = new LinkedHashSet<String>();
        for (ComparisonNode comparison : minUnit.getComparisons()) {
            String column = comparison.getColumn();
            if (!registry.isEtlMonthColumn(minUnit.getTableAlias(), column)
                    && !"cid".equalsIgnoreCase(column)) {
                columns.add(column);
            }
        }
        return new ArrayList<String>(columns);
    }

    private String resolveJoinAlias(Map<String, String> aliasJoinMap, String tableAlias, String column) {
        if (column != null && !column.isEmpty()) {
            String columnKey = tableAlias + "#" + column;
            if (aliasJoinMap.containsKey(columnKey)) {
                return aliasJoinMap.get(columnKey);
            }
        }
        return aliasJoinMap.get(tableAlias);
    }

    private String buildWhereClause(String alias, List<ComparisonNode> comparisons) {
        List<String> parts = new ArrayList<String>();
        for (ComparisonNode comparison : comparisons) {
            parts.add(comparison.toSqlFragment(alias));
        }
        return joinWithAnd(parts);
    }

    private String joinWithAnd(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                sb.append("\n\tand ");
            }
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    private String nextJoinAlias() {
        return "jtb" + joinIndex++;
    }

    private static class BuildContext {
        private final Map<String, String> aliasJoinMap = new LinkedHashMap<String, String>();

        void registerMinUnit(ConditionNode minUnit, String joinAlias) {
            String tableAlias = minUnit.getTableAlias();
            aliasJoinMap.put(tableAlias, joinAlias);
            for (ComparisonNode comparison : minUnit.getComparisons()) {
                String column = comparison.getColumn();
                if (!"etl_month".equalsIgnoreCase(column) && !"cid".equalsIgnoreCase(column)) {
                    aliasJoinMap.put(tableAlias + "#" + column, joinAlias);
                }
            }
        }

        void register(String tableAlias, String joinAlias) {
            aliasJoinMap.put(tableAlias, joinAlias);
        }

        void registerBranchAliases(Map<String, String> branchMap, String unionJoinAlias) {
            for (String tableAlias : branchMap.keySet()) {
                aliasJoinMap.put(tableAlias, unionJoinAlias);
            }
        }

        Map<String, String> getAliasJoinMap() {
            return aliasJoinMap;
        }
    }

    private static class JoinUnit {
        private final String joinAlias;
        private final String subquery;
        private final List<String> selectColumns;

        private JoinUnit(String joinAlias, String subquery, List<String> selectColumns) {
            this.joinAlias = joinAlias;
            this.subquery = subquery;
            this.selectColumns = selectColumns;
        }
    }

    private static class BranchSql {
        private final String branchAlias;
        private final String body;
        private final String cidColumn;
        private final List<String> outputColumns;
        private final Map<String, String> columnSelectMap;
        private final Map<String, String> tableAliasJoinMap;
        private final List<String> localPredicates;

        private BranchSql(String branchAlias, String body, String cidColumn, List<String> outputColumns,
                          Map<String, String> columnSelectMap, Map<String, String> tableAliasJoinMap) {
            this(branchAlias, body, cidColumn, outputColumns, columnSelectMap, tableAliasJoinMap, new ArrayList<String>());
        }

        private BranchSql(String branchAlias, String body, String cidColumn, List<String> outputColumns,
                          Map<String, String> columnSelectMap, Map<String, String> tableAliasJoinMap,
                          List<String> localPredicates) {
            this.branchAlias = branchAlias;
            this.body = body;
            this.cidColumn = cidColumn;
            this.outputColumns = outputColumns;
            this.columnSelectMap = columnSelectMap;
            this.tableAliasJoinMap = tableAliasJoinMap;
            this.localPredicates = localPredicates;
        }
    }
}
