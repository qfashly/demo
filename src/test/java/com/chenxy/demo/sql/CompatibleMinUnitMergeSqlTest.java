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

/**
 * 同表 etl_month 兼容时合并 jtb 子查询的优化场景
 */
public class CompatibleMinUnitMergeSqlTest {

    private static final Pattern SPACE = Pattern.compile("\\s+");

    private List<TableInfo> userTableInfos() {
        return Arrays.asList(
                new TableInfo("t_chara_op_cost_water", "t2", "water_usage_amt_last_1m", "string", "前1月用水金额"),
                new TableInfo("t_chara_op_cost_water", "t2", "etl_month", "date", "月份"),
                new TableInfo("t_chara_op_cost_water", "t2", "water_usage_amt_last_2m", "string", "前2月用水金额"),
                new TableInfo("t_chara_op_cost_insu", "t1", "soc_pay_per_num_1m", "int(10)", "前1月社保缴纳人数"),
                new TableInfo("t_chara_op_cost_insu", "t1", "etl_month", "date", "月份"),
                new TableInfo("t_chara_op_cost_water", "t2", "water_usage_num_last_1m", "string", "前1月用水量"),
                new TableInfo("t_chara_op_cost_water", "t2", "water_usage_num_last_2m", "string", "前2月用水量"),
                new TableInfo("t_chara_op_cost_insu", "t1", "soc_pay_per_num_2m", "int(10)", "前2月社保缴纳人数")
        );
    }

    @Test
    public void compatibleT2MinUnitsMergedIntoSingleJtb() {
        SqlQueryConfig config = new SqlQueryConfig();
        config.setMainTableSchema("tfedbqa");
        SqlQueryService service = new SqlQueryService(userTableInfos(), config);
        String condition = "( t2.etl_month <= '2026-07-01' and t2.water_usage_amt_last_1m = 100) "
                + "and ( t2.etl_month >= '2026-03-01' and t2.water_usage_amt_last_2m = 1000) "
                + "and ( t1.etl_month = '2026-07-01' and t1.soc_pay_per_num_1m = 1)";
        SqlBuildResult result = service.build(condition);

        Assert.assertEquals(ConditionType.SATISFIABLE, result.getValidationResult().getConditionType());
        String sql = normalize(result.getQuerySql());
        Assert.assertTrue(sql.contains("t2.water_usage_amt_last_1m = 100"));
        Assert.assertTrue(sql.contains("t2.water_usage_amt_last_2m = 1000"));
        Assert.assertTrue(sql.contains("t2.etl_month <= '2026-07-01'")
                || sql.contains("t2.etl_month between '2026-03-01' and '2026-07-01'"));
        Assert.assertTrue(sql.contains("t2.etl_month >= '2026-03-01'")
                || sql.contains("t2.etl_month between '2026-03-01' and '2026-07-01'"));
        Assert.assertEquals(2, countOccurrences(sql, ") jtb"));
        Assert.assertEquals(1, countOccurrences(sql, "t_chara_op_cost_water"));
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

    private String normalize(String sql) {
        return SPACE.matcher(sql == null ? "" : sql.trim().toLowerCase()).replaceAll(" ");
    }
}
