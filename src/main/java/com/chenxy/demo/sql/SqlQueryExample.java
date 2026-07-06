package com.chenxy.demo.sql;

import com.chenxy.demo.sql.model.SqlBuildResult;
import com.chenxy.demo.sql.model.SqlQueryConfig;
import com.chenxy.demo.sql.model.TableInfo;

import java.util.Arrays;
import java.util.List;

/**
 * SQL 组装使用示例
 */
public class SqlQueryExample {

    public static void main(String[] args) {
        exampleCase61();
        exampleCase62();
        exampleCase63();
    }

    private static void exampleCase61() {
        List<TableInfo> tableInfos = Arrays.asList(
                new TableInfo("tb1", "t1", "etl_month", "VARCHAR", "字段etl_month"),
                new TableInfo("tb1", "t1", "c1", "VARCHAR", "字段c1")
        );
        SqlQueryService service = new SqlQueryService(tableInfos);
        SqlBuildResult result = service.build("(etl_month = '2026-05-01' and c1 = '28')");
        printResult("用例6.1", result);
    }

    private static void exampleCase62() {
        List<TableInfo> tableInfos = Arrays.asList(
                new TableInfo("tb1", "t1", "etl_month", "VARCHAR", "字段etl_month"),
                new TableInfo("tb1", "t1", "c1", "VARCHAR", "字段c1"),
                new TableInfo("tb2", "t2", "etl_month", "VARCHAR", "字段etl_month"),
                new TableInfo("tb2", "t2", "c2", "VARCHAR", "字段c2")
        );
        SqlQueryService service = new SqlQueryService(tableInfos);
        String condition = "(t1.etl_month = '2026-05-01' and t1.c1 = '28') and (t2.etl_month = '2026-05-01' and t2.c2 = '28')";
        SqlBuildResult result = service.build(condition);
        printResult("用例6.2", result);
    }

    private static void exampleCase63() {
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
        printResult("用例6.3", result);
    }

    private static void exampleRealWorld() {
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
        printResult("真实业务示例", result);
    }

    private static void printResult(String title, SqlBuildResult result) {
        System.out.println("========== " + title + " ==========");
        System.out.println("校验结果: " + result.getValidationResult().getConditionType()
                + " - " + result.getValidationResult().getMessage());
        System.out.println("--- COUNT SQL ---");
        System.out.println(result.getCountSql());
        System.out.println("--- QUERY SQL ---");
        System.out.println(result.getQuerySql());
        System.out.println();
    }
}
