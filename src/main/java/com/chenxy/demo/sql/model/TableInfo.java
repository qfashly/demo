package com.chenxy.demo.sql.model;

/**
 * 表字段元信息
 */
public class TableInfo {

    /** 表名 */
    private String tableName;
    /** 表别名 */
    private String alias;
    /** 列名称 */
    private String column;
    /** 列类型 */
    private String type;
    /** 列描述 */
    private String columnDec;

    public TableInfo() {
    }

    public TableInfo(String tableName, String alias, String column, String type, String columnDec) {
        this.tableName = tableName;
        this.alias = alias;
        this.column = column;
        this.type = type;
        this.columnDec = columnDec;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getColumn() {
        return column;
    }

    public void setColumn(String column) {
        this.column = column;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getColumnDec() {
        return columnDec;
    }

    public void setColumnDec(String columnDec) {
        this.columnDec = columnDec;
    }
}
