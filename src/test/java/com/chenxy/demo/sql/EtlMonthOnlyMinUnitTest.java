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
 * etl_month 单独作为最小条件单元（无业务字段）的场景
 */
public class EtlMonthOnlyMinUnitTest {

    private static final Pattern SPACE = Pattern.compile("\\s+");

    private List<TableInfo> userTableInfos() {
        return Arrays.asList(
                new TableInfo("t_chara_op_cost_insu", "t1", "soc_pay_per_num_2m", "int(10)", "前2月社保缴纳人数"),
                new TableInfo("t_chara_op_cost_insu", "t1", "etl_month", "date", "月份"),
                new TableInfo("t_chara_op_cost_water", "t2", "water_usage_amt_last_2m", "string", "前2月用水金额"),
                new TableInfo("t_chara_op_cost_water", "t2", "etl_month", "date", "月份"),
                new TableInfo("t_chara_op_cost_water", "t2", "water_usage_amt_last_3m", "string", "前3月用水金额"),
                new TableInfo("t_chara_op_cost_water", "t2", "water_usage_amt_last_4m", "string", "前4月用水金额"),
                new TableInfo("t_chara_op_cost_insu", "t1", "soc_pay_per_num_3m", "int(10)", "前3月社保缴纳人数"),
                new TableInfo("t_chara_op_cost_insu", "t1", "soc_pay_per_num_4m", "int(10)", "前4月社保缴纳人数"),
                new TableInfo("t_chara_op_cost_insu", "t1", "soc_account_num", "int(10)", "社保账户个数"),
                new TableInfo("t_chara_op_cost_insu", "t1", "soc_open_month_max", "int(10)", "社保开户距今最长月份数")
        );
    }

    @Test
    public void t2EtlMonthSubqueryOnlyWithT1BusinessCondition() {
        SqlQueryService service = new SqlQueryService(userTableInfos());
        String condition = "(( ( t1.etl_month = '2026-07-01' and t1.soc_pay_per_num_2m >= 0) ) "
                + "and t2.etl_month = ( select max(etl_month) from tfedbqa.t_chara_op_cost_water ))";
        SqlBuildResult result = service.build(condition);

        Assert.assertEquals(ConditionType.SATISFIABLE, result.getValidationResult().getConditionType());
        String sql = normalize(result.getQuerySql());
        Assert.assertTrue(sql.contains("t1.etl_month = '2026-07-01'"));
        Assert.assertTrue(sql.contains("t1.soc_pay_per_num_2m >= 0"));
        Assert.assertTrue(sql.contains("t2.etl_month = (select max(etl_month) from tfedbqa.t_chara_op_cost_water)"));
        Assert.assertTrue(sql.contains("jtb1"));
        Assert.assertTrue(sql.contains("jtb2"));
        Assert.assertTrue(sql.contains("t2.water_usage_amt_last_2m"));
        Assert.assertTrue(sql.contains("t2.water_usage_amt_last_3m"));
        Assert.assertTrue(sql.contains("t2.water_usage_amt_last_4m"));
        Assert.assertTrue(sql.contains("t2.etl_month"));
        Assert.assertTrue(sql.contains("jtb2.water_usage_amt_last_2m as water_usage_amt_last_2m"));
        Assert.assertTrue(sql.contains("jtb2.etl_month as etl_month"));
        Assert.assertFalse(sql.contains("jtb2.cid as"));
    }

    @Test
    public void etlMonthOnlyWithoutBusinessFieldIsInvalidWhenMissingEtlMonth() {
        SqlQueryService service = new SqlQueryService(userTableInfos());
        try {
            service.build("(t1.soc_pay_per_num_2m >= 0)");
            Assert.fail("缺少 etl_month 应报错");
        } catch (IllegalArgumentException ex) {
            Assert.assertTrue(ex.getMessage().contains("必须包含 etl_month"));
        }
    }

    private String normalize(String sql) {
        return SPACE.matcher(sql == null ? "" : sql.trim().toLowerCase()).replaceAll(" ");
    }
}
