package com.chenxy.demo.sql.model;

/**
 * SQL 组装配置
 */
public class SqlQueryConfig {

    /** 主表 schema，可为空 */
    private String mainTableSchema;
    /** 主表名，默认 t_com_entprise_cid */
    private String mainTableName = "t_com_entprise_cid";
    /** 主表别名，默认 t0 */
    private String mainTableAlias = "t0";
    /** etl_month 字段名 */
    private String etlMonthColumn = "etl_month";
    /** 主键关联字段 */
    private String joinKeyColumn = "cid";

    public String getMainTableSchema() {
        return mainTableSchema;
    }

    public void setMainTableSchema(String mainTableSchema) {
        this.mainTableSchema = mainTableSchema;
    }

    public String getMainTableName() {
        return mainTableName;
    }

    public void setMainTableName(String mainTableName) {
        this.mainTableName = mainTableName;
    }

    public String getMainTableAlias() {
        return mainTableAlias;
    }

    public void setMainTableAlias(String mainTableAlias) {
        this.mainTableAlias = mainTableAlias;
    }

    public String getEtlMonthColumn() {
        return etlMonthColumn;
    }

    public void setEtlMonthColumn(String etlMonthColumn) {
        this.etlMonthColumn = etlMonthColumn;
    }

    public String getJoinKeyColumn() {
        return joinKeyColumn;
    }

    public void setJoinKeyColumn(String joinKeyColumn) {
        this.joinKeyColumn = joinKeyColumn;
    }

    public String qualifiedMainTable() {
        if (mainTableSchema == null || mainTableSchema.trim().isEmpty()) {
            return mainTableName;
        }
        return mainTableSchema + "." + mainTableName;
    }
}
