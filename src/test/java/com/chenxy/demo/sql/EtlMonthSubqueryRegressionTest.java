package com.chenxy.demo.sql;

import com.chenxy.demo.sql.model.ConditionType;
import com.chenxy.demo.sql.model.SqlBuildResult;
import com.chenxy.demo.sql.model.TableInfo;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class EtlMonthSubqueryRegressionTest {

    private static final Pattern SPACE = Pattern.compile("\\s+");

    private List<TableInfo> baseTables() {
        return Arrays.asList(
                new TableInfo("tb1", "t1", "etl_month", "VARCHAR", "字段etl_month"),
                new TableInfo("tb1", "t1", "c1", "VARCHAR", "字段c1"),
                new TableInfo("tb2", "t2", "etl_month", "VARCHAR", "字段etl_month"),
                new TableInfo("tb2", "t2", "c2", "VARCHAR", "字段c2")
        );
    }

    @Test
    public void singleGroupWithSubqueryEtlMonth() {
        SqlQueryService service = new SqlQueryService(baseTables());
        String condition = "(t1.etl_month = (select max(etl_month) from table1) and t1.c1 = '28')";
        SqlBuildResult result = service.build(condition);
        Assert.assertEquals(ConditionType.SATISFIABLE, result.getValidationResult().getConditionType());
        Assert.assertTrue(normalize(result.getQuerySql()).contains("t1.etl_month = (select max(etl_month) from table1)"));
    }

    @Test
    public void subqueryEtlMonthWithAnotherTable() {
        SqlQueryService service = new SqlQueryService(baseTables());
        String condition = "(t1.etl_month = (select max(etl_month) from table1) and t1.c1 = '28') "
                + "and (t2.etl_month = '2026-05-01' and t2.c2 = '28')";
        SqlBuildResult result = service.build(condition);
        Assert.assertEquals(ConditionType.SATISFIABLE, result.getValidationResult().getConditionType());
        Assert.assertTrue(normalize(result.getQuerySql()).contains("t1.etl_month = (select max(etl_month) from table1)"));
    }

    @Test
    public void splitEtlMonthAndBusinessAcrossGroups() {
        SqlQueryService service = new SqlQueryService(baseTables());
        String condition = "(t1.etl_month = (select max(etl_month) from table1)) and (t1.c1 = '28')";
        SqlBuildResult result = service.build(condition);
        Assert.assertEquals(ConditionType.SATISFIABLE, result.getValidationResult().getConditionType());
        Assert.assertTrue(normalize(result.getQuerySql()).contains("t1.etl_month = (select max(etl_month) from table1)"));
    }

    @Test
    public void sameTableSubqueryAndLiteralEtlMonthKeepsBothJoins() {
        List<TableInfo> tables = Arrays.asList(
                new TableInfo("tb1", "t1", "etl_month", "VARCHAR", "字段etl_month"),
                new TableInfo("tb1", "t1", "c1", "VARCHAR", "字段c1"),
                new TableInfo("tb1", "t1", "c2", "VARCHAR", "字段c2")
        );
        SqlQueryService service = new SqlQueryService(tables);
        String condition = "(t1.etl_month = (select max(etl_month) from table1) and t1.c1 = '28') "
                + "and (t1.etl_month = '2026-05-01' and t1.c2 = '30')";
        SqlBuildResult result = service.build(condition);
        Assert.assertEquals(ConditionType.SATISFIABLE, result.getValidationResult().getConditionType());
        String sql = normalize(result.getQuerySql());
        Assert.assertTrue(sql.contains("t1.etl_month = (select max(etl_month) from table1)"));
        Assert.assertTrue(sql.contains("t1.etl_month = '2026-05-01'"));
        Assert.assertTrue(sql.contains("jtb1"));
        Assert.assertTrue(sql.contains("jtb2"));
    }

    private String normalize(String sql) {
        return SPACE.matcher(sql == null ? "" : sql.trim().toLowerCase()).replaceAll(" ");
    }
}
