package com.chenxy.demo.sql.model;

/**
 * 表字段元信息
 */
public class CreateTableInfo {
    /** 列名称 */
    private String column;
    /** 列类型 */
    private String type;
    /** 列描述 */
    private String columnDec;

    public CreateTableInfo() {
    }

    public CreateTableInfo(String column, String type, String columnDec) {
        this.column = column;
        this.type = type;
        this.columnDec = columnDec;
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
