package com.chenxy.demo.sql;

import com.chenxy.demo.sql.model.ConditionType;
import com.chenxy.demo.sql.model.SqlBuildResult;
import com.chenxy.demo.sql.model.SqlQueryConfig;
import com.chenxy.demo.sql.model.TableInfo;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class SqlQueryServiceTest {

    private static final Pattern SPACE = Pattern.compile("\\s+");

    @Test
    public void testCase61_singleTableCondition() {
        List<TableInfo> tableInfos = Arrays.asList(
                new TableInfo("tb1", "t1", "etl_month", "VARCHAR", "字段etl_month"),
                new TableInfo("tb1", "t1", "c1", "VARCHAR", "字段c1")
        );
        SqlQueryService service = new SqlQueryService(tableInfos);
        SqlBuildResult result = service.build("(etl_month = '2026-05-01' and c1 = '28')");

        Assert.assertEquals(ConditionType.SATISFIABLE, result.getValidationResult().getConditionType());
        Assert.assertNotNull(result.getCountSql());
        Assert.assertNotNull(result.getQuerySql());


        String expectedQuery = ""
                + "select t0.cid as cid, t0.ent_name as ent_name, t0.uni_scid as uni_scid, jtb1.c1 as c1\n"
                + "from \n"
                + "(\n"
                + "\tselect cid, ent_name, uni_scid\n"
                + "\tfrom t_com_entprise_cid \n"
                + ") t0\n"
                + "join \n"
                + "(\n"
                + "\tselect t1.cid, t1.c1\n"
                + "\tfrom tb1 t1\n"
                + "\twhere t1.etl_month = '2026-05-01'\n"
                + "\tand t1.c1 = '28'\n"
                + ") jtb1 on t0.cid = jtb1.cid";

        assertSqlEquals(expectedQuery, result.getQuerySql());
        assertSqlContains(result.getCountSql(), "select count(1) as cnt", "join", "jtb1");
    }

    @Test
    public void testCase62_andTwoTables() {
        List<TableInfo> tableInfos = Arrays.asList(
                new TableInfo("tb1", "t1", "etl_month", "VARCHAR", "字段etl_month"),
                new TableInfo("tb1", "t1", "c1", "VARCHAR", "字段c1"),
                new TableInfo("tb2", "t2", "etl_month", "VARCHAR", "字段etl_month"),
                new TableInfo("tb2", "t2", "c2", "VARCHAR", "字段c2")
        );
        SqlQueryService service = new SqlQueryService(tableInfos);
        String condition = "(t1.etl_month = '2026-05-01' and t1.c1 = '28') and (t2.etl_month = '2026-05-01' and t2.c2 = '28')";
        SqlBuildResult result = service.build(condition);

        Assert.assertEquals(ConditionType.SATISFIABLE, result.getValidationResult().getConditionType());

        String expectedQuery = ""
                + "select t0.cid as cid, t0.ent_name as ent_name, t0.uni_scid as uni_scid, jtb1.c1 as c1, jtb2.c2 as c2\n"
                + "from \n"
                + "(\n"
                + "\tselect cid, ent_name, uni_scid\n"
                + "\tfrom t_com_entprise_cid \n"
                + ") t0\n"
                + "join \n"
                + "(\n"
                + "\tselect t1.cid, t1.c1\n"
                + "\tfrom tb1 t1\n"
                + "\twhere t1.etl_month = '2026-05-01'\n"
                + "\tand t1.c1 = '28'\n"
                + ") jtb1 on t0.cid = jtb1.cid\n"
                + "join \n"
                + "(\n"
                + "\tselect t2.cid, t2.c2\n"
                + "\tfrom tb2 t2\n"
                + "\twhere t2.etl_month = '2026-05-01'\n"
                + "\tand t2.c2 = '28'\n"
                + ") jtb2 on t0.cid = jtb2.cid";

        assertSqlEquals(expectedQuery, result.getQuerySql());
        assertSqlContains(result.getCountSql(), "select count(1) as cnt", "jtb1", "jtb2");
    }

    @Test
    public void testCase63_orAndMixedCondition() {
        List<TableInfo> tableInfos = Arrays.asList(
                new TableInfo("tb1", "t1", "etl_month", "VARCHAR", "字段etl_month"),
                new TableInfo("tb1", "t1", "c1", "VARCHAR", "字段c1"),
                new TableInfo("tb2", "t2", "etl_month", "VARCHAR", "字段etl_month"),
                new TableInfo("tb2", "t2", "c2", "VARCHAR", "字段c2"),
                new TableInfo("tb3", "t3", "etl_month", "VARCHAR", "字段etl_month"),
                new TableInfo("tb3", "t3", "c3", "VARCHAR", "字段c3")
        );
        SqlQueryService service = new SqlQueryService(tableInfos);
        String condition = "(((t1.etl_month = '2026-05-01' and t1.c1 = '28') or (t2.etl_month = '2026-05-01' and t2.c2 = '28')) and (t3.etl_month = '2026-05-01' and t3.c3 = '28'))";
        SqlBuildResult result = service.build(condition);

        Assert.assertEquals(ConditionType.SATISFIABLE, result.getValidationResult().getConditionType());

        assertSqlContains(result.getQuerySql(),
                "select t0.cid as cid, t0.ent_name as ent_name, t0.uni_scid as uni_scid, jtb1.c1 as c1, jtb1.c2 as c2, jtb2.c3 as c3",
                "union",
                "from tb1 t1",
                "from tb2 t2",
                "select t3.cid, t3.c3",
                "from tb3 t3",
                ") jtb1 on t0.cid = jtb1.cid",
                ") jtb2 on t0.cid = jtb2.cid");
    }

    @Test
    public void testRealWorldExample() {
        List<TableInfo> tableInfos = Arrays.asList(
                new TableInfo("t_chara_basic_info_indu_comm", "t1", "etl_month", "VARCHAR", "字段etl_month"),
                new TableInfo("t_chara_basic_info_indu_comm", "t1", "bus_login_month", "INT", "登录月份"),
                new TableInfo("t_chara_basic_info_indu_comm", "t1", "bus_ind_type_name", "VARCHAR", "行业类型"),
                new TableInfo("t_chara_basic_info_change", "t2", "etl_month", "VARCHAR", "字段etl_month"),
                new TableInfo("t_chara_basic_info_change", "t2", "bus_le_rep_cha_times_1y", "INT", "1年变更次数"),
                new TableInfo("t_chara_basic_info_change", "t2", "bus_le_rep_cha_times_5y", "INT", "5年变更次数")
        );
        SqlQueryConfig config = new SqlQueryConfig();
        config.setMainTableSchema("tfedb");
        config.setMainTableName("t_com_entprise_cid");
        SqlQueryService service = new SqlQueryService(tableInfos, config);

        String condition = "(t1.etl_month = '2026-03-01' and t1.bus_login_month >= 24) and (t2.etl_month = '2026-03-01' and t2.bus_le_rep_cha_times_5y <= 3)";
        SqlBuildResult result = service.build(condition);

        Assert.assertEquals(ConditionType.SATISFIABLE, result.getValidationResult().getConditionType());
        assertSqlContains(result.getQuerySql(),
                "from tfedb.t_com_entprise_cid",
                "t_chara_basic_info_indu_comm",
                "bus_login_month >= 24",
                "t_chara_basic_info_change",
                "bus_le_rep_cha_times_5y <= 3",
                "jtb1.bus_login_month as bus_login_month",
                "jtb2.bus_le_rep_cha_times_5y as bus_le_rep_cha_times_5y");
    }

    @Test
    public void testContradictionCondition() {
        List<TableInfo> tableInfos = Arrays.asList(
                new TableInfo("tb1", "t1", "etl_month", "VARCHAR", "字段etl_month"),
                new TableInfo("tb1", "t1", "c1", "VARCHAR", "字段c1")
        );
        SqlQueryService service = new SqlQueryService(tableInfos);
        SqlBuildResult result = service.build("(t1.etl_month = '2026-05-01' and t1.c1 = '28' and t1.c1 = '30')");

        Assert.assertEquals(ConditionType.CONTRADICTION, result.getValidationResult().getConditionType());
        Assert.assertNull(result.getQuerySql());
    }

    @Test
    public void testConditionReorderKeepsLogic() {
        List<TableInfo> tableInfos = Arrays.asList(
                new TableInfo("tb1", "t1", "etl_month", "VARCHAR", "字段etl_month"),
                new TableInfo("tb1", "t1", "c1", "VARCHAR", "字段c1"),
                new TableInfo("tb2", "t2", "etl_month", "VARCHAR", "字段etl_month"),
                new TableInfo("tb2", "t2", "c2", "VARCHAR", "字段c2")
        );
        SqlQueryService service = new SqlQueryService(tableInfos);
        String original = "(t2.etl_month = '2026-05-01' and t2.c2 = '28') and (t1.etl_month = '2026-05-01' and t1.c1 = '28')";
        String reordered = "(t1.etl_month = '2026-05-01' and t1.c1 = '28') and (t2.etl_month = '2026-05-01' and t2.c2 = '28')";

        SqlBuildResult originalResult = service.build(original);
        SqlBuildResult reorderedResult = service.build(reordered);

        assertSqlEquals(normalizeSql(originalResult.getQuerySql()), normalizeSql(reorderedResult.getQuerySql()));
    }

    private void assertSqlEquals(String expected, String actual) {
        Assert.assertEquals(normalizeSql(expected), normalizeSql(actual));
    }

    private void assertSqlContains(String actual, String... parts) {
        String normalized = normalizeSql(actual);
        for (String part : parts) {
            Assert.assertTrue("SQL应包含: " + part + "\n实际SQL:\n" + actual, normalized.contains(normalizeSql(part)));
        }
    }

    private String normalizeSql(String sql) {
        if (sql == null) {
            return "";
        }
        return SPACE.matcher(sql.trim().toLowerCase()).replaceAll(" ");
    }
}
