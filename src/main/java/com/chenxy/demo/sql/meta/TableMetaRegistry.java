package com.chenxy.demo.sql.meta;


import com.chenxy.demo.sql.model.TableInfo;

import java.util.*;

/**
 * 表元数据注册中心，基于 {@link com.chenxy.demo.sql.model.TableInfo} 列表构建索引。
 *
 * <p>提供字段解析（{@link #resolve}）、业务列查询（{@link #getBusinessColumns}）、
 * 输出列查询（{@link #getOutputColumns}）等能力，供解析器与 SQL 组装器使用。
 */
public class TableMetaRegistry {

    private final Map<String, TableInfo> aliasColumnMap = new HashMap<String, TableInfo>();
    private final Map<String, String> aliasTableMap = new HashMap<String, String>();
    private final Map<String, String> columnAliasMap = new HashMap<String, String>();
    private final Map<String, List<String>> aliasColumns = new LinkedHashMap<String, List<String>>();
    private final Set<String> etlMonthColumns = new HashSet<String>();

    public TableMetaRegistry(List<TableInfo> tableInfos) {
        if (tableInfos == null) {
            return;
        }
        for (TableInfo info : tableInfos) {
            aliasColumnMap.put(key(info.getAlias(), info.getColumn()), info);
            aliasTableMap.put(info.getAlias(), info.getTableName());
            columnAliasMap.put(info.getColumn(), info.getAlias());
            if (!aliasColumns.containsKey(info.getAlias())) {
                aliasColumns.put(info.getAlias(), new ArrayList<String>());
            }
            aliasColumns.get(info.getAlias()).add(info.getColumn());
            if ("etl_month".equalsIgnoreCase(info.getColumn())) {
                etlMonthColumns.add(info.getAlias());
            }
        }
    }

    public TableInfo resolve(String tableAlias, String column) {
        if (tableAlias != null && !tableAlias.isEmpty()) {
            TableInfo info = aliasColumnMap.get(key(tableAlias, column));
            if (info == null) {
                throw new IllegalArgumentException("未知字段: " + tableAlias + "." + column);
            }
            return info;
        }
        String alias = columnAliasMap.get(column);
        if (alias == null) {
            throw new IllegalArgumentException("未知字段: " + column);
        }
        if (countAliasByColumn(column) > 1) {
            throw new IllegalArgumentException("字段 " + column + " 存在于多张表，请指定表别名");
        }
        return aliasColumnMap.get(key(alias, column));
    }

    public String getTableName(String alias) {
        String tableName = aliasTableMap.get(alias);
        if (tableName == null) {
            throw new IllegalArgumentException("未知表别名: " + alias);
        }
        return tableName;
    }

    /**
     * 业务列：除 etl_month、cid 外的字段（用于 OR 分支 UNION 等场景）。
     */
    public List<String> getBusinessColumns(String alias) {
        List<String> columns = new ArrayList<String>();
        List<String> all = aliasColumns.get(alias);
        if (all == null) {
            return columns;
        }
        for (String column : all) {
            if (!"etl_month".equalsIgnoreCase(column) && !"cid".equalsIgnoreCase(column)) {
                columns.add(column);
            }
        }
        return columns;
    }

    /**
     * 输出列：tableInfos 中该表除 cid 外的全部字段（含 etl_month）。
     * <p>用于「仅有 etl_month 条件、无业务字段过滤」的 MIN_UNIT，此时 jtb 需输出整表列。
     */
    public List<String> getOutputColumns(String alias) {
        LinkedHashSet<String> columns = new LinkedHashSet<String>();
        List<String> all = aliasColumns.get(alias);
        if (all == null) {
            return new ArrayList<String>();
        }
        for (String column : all) {
            if (!"cid".equalsIgnoreCase(column)) {
                columns.add(column);
            }
        }
        return new ArrayList<String>(columns);
    }

    public Set<String> getAllAliases() {
        return aliasColumns.keySet();
    }

    public boolean isEtlMonthColumn(String alias, String column) {
        return "etl_month".equalsIgnoreCase(column);
    }

    private int countAliasByColumn(String column) {
        int count = 0;
        for (String alias : aliasColumns.keySet()) {
            if (aliasColumns.get(alias).contains(column)) {
                count++;
            }
        }
        return count;
    }

    private String key(String alias, String column) {
        return alias + "#" + column;
    }

    public List<String> getOrderedBusinessColumns(Set<String> aliases) {
        List<String> ordered = new ArrayList<String>();
        for (Map.Entry<String, List<String>> entry : aliasColumns.entrySet()) {
            if (aliases.contains(entry.getKey())) {
                ordered.addAll(getBusinessColumns(entry.getKey()));
            }
        }
        return ordered;
    }

    public Set<String> toAliasSet(List<String> aliases) {
        return aliases == null ? Collections.<String>emptySet() : new LinkedHashSet<String>(aliases);
    }
}
