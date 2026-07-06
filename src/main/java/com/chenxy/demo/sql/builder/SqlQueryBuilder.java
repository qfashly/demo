package com.chenxy.demo.sql.builder;

import com.chenxy.demo.sql.meta.TableMetaRegistry;
import com.chenxy.demo.sql.model.ComparisonNode;
import com.chenxy.demo.sql.model.ConditionNode;
import com.chenxy.demo.sql.model.SqlQueryConfig;
import com.chenxy.demo.sql.model.TableInfo;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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
        List<JoinUnit> joinUnits = buildJoinUnits(condition);
        Set<String> involvedAliases = collectAliases(condition);

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
        return sql.toString().trim();
    }

    private String buildSelectColumns(List<JoinUnit> joinUnits) {
        List<String> columns = new ArrayList<String>();
        columns.add(config.getMainTableAlias() + ".cid");
        columns.add(config.getMainTableAlias() + ".ent_name");
        columns.add(config.getMainTableAlias() + ".uni_scid");
        for (JoinUnit joinUnit : joinUnits) {
            columns.addAll(joinUnit.selectColumns);
        }
        return joinColumns(columns);
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

    private List<JoinUnit> buildJoinUnits(ConditionNode node) {
        List<JoinUnit> units = new ArrayList<JoinUnit>();
        if (node.getType() == ConditionNode.NodeType.AND) {
            for (ConditionNode child : node.getChildren()) {
                units.addAll(buildJoinUnits(child));
            }
            return units;
        }
        if (node.getType() == ConditionNode.NodeType.OR) {
            units.add(buildUnionJoinUnit(node));
            return units;
        }
        if (node.getType() == ConditionNode.NodeType.MIN_UNIT) {
            units.add(buildSingleTableJoinUnit(node));
            return units;
        }
        throw new IllegalArgumentException("不支持的条件节点类型: " + node.getType());
    }

    private JoinUnit buildSingleTableJoinUnit(ConditionNode minUnit) {
        String alias = minUnit.getTableAlias();
        String tableName = minUnit.getTableName();
        String joinAlias = nextJoinAlias();
        List<String> businessColumns = registry.getBusinessColumns(alias);

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
            selectColumns.add(joinAlias + "." + column);
        }
        return new JoinUnit(joinAlias, subquery.toString(), selectColumns);
    }

    private JoinUnit buildUnionJoinUnit(ConditionNode orNode) {
        String joinAlias = nextJoinAlias();
        List<ConditionNode> branches = orNode.getChildren();
        Set<String> allColumns = new LinkedHashSet<String>();
        for (ConditionNode branch : branches) {
            if (branch.getType() != ConditionNode.NodeType.MIN_UNIT) {
                throw new IllegalArgumentException("OR 分支必须是同一最小查询条件单元");
            }
            allColumns.addAll(registry.getBusinessColumns(branch.getTableAlias()));
        }

        StringBuilder subquery = new StringBuilder();
        for (int i = 0; i < branches.size(); i++) {
            ConditionNode branch = branches.get(i);
            String alias = branch.getTableAlias();
            String tableName = branch.getTableName();
            if (i > 0) {
                subquery.append("\n\t\n\tunion\n\t\n");
            }
            subquery.append("\tselect ").append(alias).append(".cid");
            for (String column : allColumns) {
                if (containsBusinessColumn(branch, column)) {
                    subquery.append(", ").append(alias).append(".").append(column);
                } else {
                    subquery.append(", null as ").append(column);
                }
            }
            subquery.append(" from ").append(tableName).append(" ").append(alias).append("\n");
            subquery.append("\twhere ").append(buildWhereClause(alias, branch.getComparisons())).append("\n");
        }

        List<String> selectColumns = new ArrayList<String>();
        for (String column : allColumns) {
            selectColumns.add(joinAlias + "." + column);
        }
        return new JoinUnit(joinAlias, subquery.toString(), selectColumns);
    }

    private boolean containsBusinessColumn(ConditionNode minUnit, String column) {
        return registry.getBusinessColumns(minUnit.getTableAlias()).contains(column);
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

    private Set<String> collectAliases(ConditionNode node) {
        Set<String> aliases = new LinkedHashSet<String>();
        collectAliasesInternal(node, aliases);
        return aliases;
    }

    private void collectAliasesInternal(ConditionNode node, Set<String> aliases) {
        if (node.getType() == ConditionNode.NodeType.MIN_UNIT) {
            aliases.add(node.getTableAlias());
            return;
        }
        for (ConditionNode child : node.getChildren()) {
            collectAliasesInternal(child, aliases);
        }
    }

    private String nextJoinAlias() {
        return "jtb" + joinIndex++;
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
}
