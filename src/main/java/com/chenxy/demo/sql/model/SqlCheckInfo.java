package com.chenxy.demo.sql.model;

/**
 * packageName com.scredit.crs.sql.model
 *
 * @author chenxy
 * @className SqlCheckInfo
 * @date 07 7月 2026 11:36
 * @Version 1.0.0
 * @description TODO
 */
public class SqlCheckInfo {

    private boolean check;

    private String msg;

    private SqlBuildResult sqlBuildResult;

    public boolean isCheck() {
        return check;
    }

    public void setCheck(boolean check) {
        this.check = check;
    }
}