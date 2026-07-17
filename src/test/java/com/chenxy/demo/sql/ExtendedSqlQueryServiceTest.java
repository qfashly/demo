package com.chenxy.demo.sql;

import com.chenxy.demo.sql.model.ConditionType;
import com.chenxy.demo.sql.model.SqlBuildResult;
import com.chenxy.demo.sql.model.TableInfo;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 扩展 SQL 能力测试
 */
public class ExtendedSqlQueryServiceTest {

    private static final Pattern SPACE = Pattern.compile("\\s+");

    private List<TableInfo> baseTables() {
        return Arrays.asList(
                new TableInfo("tb1", "t1", "etl_month", "VARCHAR", "字段etl_month"),
                new TableInfo("tb1", "t1", "c1", "VARCHAR", "字段c1"),
                new TableInfo("tb2", "t2", "etl_month", "VARCHAR", "字段etl_month"),
                new TableInfo("tb2", "t2", "c2", "VARCHAR", "字段c2"),
                new TableInfo("tb3", "t3", "etl_month", "VARCHAR", "字段etl_month"),
                new TableInfo("tb3", "t3", "c3", "VARCHAR", "字段c3")
        );
    }

    @Test
    public void testCrossTableComparison() {
        SqlQueryService service = new SqlQueryService(baseTables());
        String condition = "(t1.etl_month = '2026-05-01' and t1.c1 = '28') and (t2.etl_month = '2026-05-01' and t2.c2 = '28') and (t1.c1 = t2.c2)";
        SqlBuildResult result = service.build(condition);

        Assert.assertEquals(ConditionType.SATISFIABLE, result.getValidationResult().getConditionType());
        assertSqlContains(result.getQuerySql(), "where jtb1.c1 = jtb2.c2", "jtb1", "jtb2");
        assertSqlContains(result.getCountSql(), "where jtb1.c1 = jtb2.c2");
    }

    @Test
    public void testBetweenIsNullNotLike() {
        SqlQueryService service = new SqlQueryService(baseTables());
        String condition = "(t1.etl_month BETWEEN '2026-05-01' AND '2026-06-01' and t1.c1 IS NOT NULL and t1.c1 NOT LIKE '0%')";
        SqlBuildResult result = service.build(condition);

        Assert.assertEquals(ConditionType.SATISFIABLE, result.getValidationResult().getConditionType());
        assertSqlContains(result.getQuerySql(),
                "t1.etl_month between '2026-05-01' and '2026-06-01'",
                "t1.c1 is not null",
                "t1.c1 not like '0%'");
    }

    @Test
    public void testFunctionExpression() {
        SqlQueryService service = new SqlQueryService(baseTables());
        String condition = "(t1.etl_month = '2026-05-01' and DATE(t1.etl_month) = '2026-05-01' and t1.c1 = '28')";
        SqlBuildResult result = service.build(condition);

        Assert.assertEquals(ConditionType.SATISFIABLE, result.getValidationResult().getConditionType());
        assertSqlContains(result.getQuerySql(), "date(jtb1.etl_month) = '2026-05-01'", "where date(jtb1.etl_month)");
    }

    @Test
    public void testInWithoutInputParentheses() {
        SqlQueryService service = new SqlQueryService(baseTables());
        String condition = "(t1.etl_month = '2026-05-01' and t1.c1 IN '28', '30')";
        SqlBuildResult result = service.build(condition);

        Assert.assertEquals(ConditionType.SATISFIABLE, result.getValidationResult().getConditionType());
        assertSqlContains(result.getQuerySql(), "t1.c1 in ('28', '30')");
    }

    @Test
    public void testNotInWithoutInputParentheses() {
        SqlQueryService service = new SqlQueryService(baseTables());
        String condition = "(t1.etl_month = '2026-05-01' and t1.c1 NOT IN '1', '2')";
        SqlBuildResult result = service.build(condition);

        Assert.assertEquals(ConditionType.SATISFIABLE, result.getValidationResult().getConditionType());
        assertSqlContains(result.getQuerySql(), "t1.c1 not in ('1', '2')");
    }

    @Test
    public void testInSubquery() {
        SqlQueryService service = new SqlQueryService(baseTables());
        String condition = "(t1.etl_month = '2026-05-01' and t1.c1 IN (SELECT '28' FROM tb1 WHERE cid = t1.cid))";
        SqlBuildResult result = service.build(condition);

        Assert.assertEquals(ConditionType.SATISFIABLE, result.getValidationResult().getConditionType());
        assertSqlContains(result.getQuerySql(), "t1.c1 in (select '28' from tb1 where cid = t1.cid)");
    }

    @Test
    public void testOrBranchWithMultiTableAnd() {
        SqlQueryService service = new SqlQueryService(baseTables());
        String condition = "((t1.etl_month = '2026-05-01' and t1.c1 = '28' and t2.etl_month = '2026-05-01' and t2.c2 = '28') or (t3.etl_month = '2026-05-01' and t3.c3 = '28'))";
        SqlBuildResult result = service.build(condition);

        Assert.assertEquals(ConditionType.SATISFIABLE, result.getValidationResult().getConditionType());
        assertSqlContains(result.getQuerySql(), "union", "tb1", "tb2", "tb3", "join", "jtb1");
    }

    @Test
    public void testOrBranchWithCrossTableInside() {
        SqlQueryService service = new SqlQueryService(baseTables());
        String condition = "((t1.etl_month = '2026-05-01' and t1.c1 = '28' and t2.etl_month = '2026-05-01' and t2.c2 = '28' and t1.c1 = t2.c2) or (t3.etl_month = '2026-05-01' and t3.c3 = '28'))";
        SqlBuildResult result = service.build(condition);

        Assert.assertEquals(ConditionType.SATISFIABLE, result.getValidationResult().getConditionType());
        assertSqlContains(result.getQuerySql(), "union", "br_t1.c1 = br_t2.c2", "tb3");
    }

    @Test
    public void testNestedOrInsideOrBranch() {
        SqlQueryService service = new SqlQueryService(baseTables());
        String condition = "(((t1.etl_month = '2026-05-01' and t1.c1 = '28') or (t2.etl_month = '2026-05-01' and t2.c2 = '30')) or (t3.etl_month = '2026-05-01' and t3.c3 = '28'))";
        SqlBuildResult result = service.build(condition);

        Assert.assertEquals(ConditionType.SATISFIABLE, result.getValidationResult().getConditionType());
        assertSqlContains(result.getQuerySql(), "union", "tb1", "tb2", "tb3");
    }

    @Test
    public void testSameTableDuplicateEtlMonthMerged() {
        List<TableInfo> tableInfos = Arrays.asList(
                new TableInfo("tb3", "t3", "etl_month", "VARCHAR", "字段etl_month"),
                new TableInfo("tb3", "t3", "c3", "VARCHAR", "字段c3"),
                new TableInfo("tb3", "t3", "c4", "VARCHAR", "字段c4")
        );
        SqlQueryService service = new SqlQueryService(tableInfos);
        String condition = "(t3.etl_month = '2026-05-01' and t3.c3 = '28') and (t3.etl_month = '2026-05-01' and t3.c4 = '28')";
        SqlBuildResult result = service.build(condition);

        Assert.assertEquals(ConditionType.SATISFIABLE, result.getValidationResult().getConditionType());
        assertSqlContains(result.getQuerySql(),
                "from tb3 t3",
                "t3.etl_month = '2026-05-01'",
                "t3.c3 = '28'",
                "t3.c4 = '28'");
        String normalized = normalizeSql(result.getQuerySql());
        Assert.assertEquals(1, countOccurrences(normalized, "t3.etl_month = '2026-05-01'"));
    }

    @Test
    public void testSameTableDifferentEtlMonthSnapshots() {
        List<TableInfo> tableInfos = Arrays.asList(
                new TableInfo("tb3", "t3", "etl_month", "VARCHAR", "字段etl_month"),
                new TableInfo("tb3", "t3", "c3", "VARCHAR", "字段c3"),
                new TableInfo("tb3", "t3", "c4", "VARCHAR", "字段c4"),
                new TableInfo("tb3", "t3", "c5", "VARCHAR", "字段c5")
        );
        SqlQueryService service = new SqlQueryService(tableInfos);
        String condition = "(t3.etl_month >= '2026-05-01' and t3.c3 = '28') and (t3.etl_month <= '2027-06-01' and t3.c4 = '28') and (t3.etl_month = '2027-07-01' and t3.c5 = '28')";
        SqlBuildResult result = service.build(condition);

        Assert.assertEquals(ConditionType.SATISFIABLE, result.getValidationResult().getConditionType());
        assertSqlContains(result.getQuerySql(),
                "from tb3 t3",
                "jtb1",
                "jtb2",
                "t3.etl_month between '2026-05-01' and '2027-06-01'",
                "t3.c3 = '28'",
                "t3.c4 = '28'",
                "t3.etl_month = '2027-07-01'",
                "t3.c5 = '28'",
                "jtb1.c3 as c3",
                "jtb1.c4 as c4",
                "jtb2.c5 as c5");
    }

    @Test
    public void testContradictoryEtlMonthRangeInSingleSnapshot() {
        List<TableInfo> tableInfos = Arrays.asList(
                new TableInfo("tb3", "t3", "etl_month", "VARCHAR", "字段etl_month"),
                new TableInfo("tb3", "t3", "c3", "VARCHAR", "字段c3"),
                new TableInfo("tb3", "t3", "c4", "VARCHAR", "字段c4")
        );
        SqlQueryService service = new SqlQueryService(tableInfos);
        String condition = "(t3.etl_month >= '2026-05-01' and t3.etl_month <= '2025-06-01' and t3.c3 = '28')";
        SqlBuildResult result = service.build(condition);

        Assert.assertEquals(ConditionType.CONTRADICTION, result.getValidationResult().getConditionType());
        Assert.assertNull(result.getQuerySql());
    }

    @Test
    public void testValidEtlMonthRangeMergedToBetween() {
        List<TableInfo> tableInfos = Arrays.asList(
                new TableInfo("tb3", "t3", "etl_month", "VARCHAR", "字段etl_month"),
                new TableInfo("tb3", "t3", "c3", "VARCHAR", "字段c3"),
                new TableInfo("tb3", "t3", "c4", "VARCHAR", "字段c4")
        );
        SqlQueryService service = new SqlQueryService(tableInfos);
        String condition = "(t3.etl_month >= '2025-05-01' and t3.c3 = '28') and (t3.etl_month <= '2026-06-01' and t3.c4 = '28')";
        SqlBuildResult result = service.build(condition);

        Assert.assertEquals(ConditionType.SATISFIABLE, result.getValidationResult().getConditionType());
        assertSqlContains(result.getQuerySql(),
                "from tb3 t3",
                "t3.etl_month between '2025-05-01' and '2026-06-01'",
                "t3.c3 = '28'",
                "t3.c4 = '28'");
    }

    private int countOccurrences(String text, String part) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(part, idx)) != -1) {
            count++;
            idx += part.length();
        }
        return count;
    }

    @Test
    public void testQuerySqlSelectColumnAliases() {
        SqlQueryService service = new SqlQueryService(baseTables());
        String condition = "(t1.etl_month = '2026-05-01' and t1.c1 = '28') and (t2.etl_month = '2026-05-01' and t2.c2 = '28')";
        SqlBuildResult result = service.build(condition);

        assertSqlContains(result.getQuerySql(),
                "t0.cid as cid",
                "t0.ent_name as ent_name",
                "t0.uni_scid as uni_scid",
                "jtb1.c1 as c1",
                "jtb2.c2 as c2");
    }

    @Test
    public void testEtlMonthSubquery() {
        SqlQueryService service = new SqlQueryService(baseTables());
        String condition = "(t1.etl_month = (select max(etl_month) from table1) and t1.c1 = '28')";
        SqlBuildResult result = service.build(condition);

        Assert.assertEquals(ConditionType.SATISFIABLE, result.getValidationResult().getConditionType());
        assertSqlContains(result.getQuerySql(),
                "t1.etl_month = (select max(etl_month) from table1)",
                "and t1.c1 = '28'");
    }

    @Test
    public void testEtlMonthFunctionOnRight() {
        SqlQueryService service = new SqlQueryService(baseTables());
        String condition = "(t1.etl_month = DATE('2026-05-01') and t1.c1 = '28')";
        SqlBuildResult result = service.build(condition);

        Assert.assertEquals(ConditionType.SATISFIABLE, result.getValidationResult().getConditionType());
        assertSqlContains(result.getQuerySql(), "t1.etl_month = date('2026-05-01')");
    }

    private void assertSqlContains(String actual, String... parts) {
        String normalized = normalizeSql(actual);
        for (String part : parts) {
            Assert.assertTrue("SQL应包含: " + part + "\n实际SQL:\n" + actual,
                    normalized.contains(normalizeSql(part)));
        }
    }

    private String normalizeSql(String sql) {
        if (sql == null) {
            return "";
        }
        return SPACE.matcher(sql.trim().toLowerCase()).replaceAll(" ");
    }
}
