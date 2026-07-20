package com.chenxy.demo.sql.validator;

import com.chenxy.demo.sql.SqlQueryService;
import com.chenxy.demo.sql.model.ComparisonNode;
import com.chenxy.demo.sql.model.ComparisonOperator;
import com.chenxy.demo.sql.model.ConditionNode;
import com.chenxy.demo.sql.model.ConditionType;
import com.chenxy.demo.sql.model.SqlBuildResult;
import com.chenxy.demo.sql.model.TableInfo;
import com.chenxy.demo.sql.parser.ConditionParser;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class MinUnitMergeHelperTest {

    @Test
    public void mergeThreeSnapshotsIntoTwoJoinUnits() {
        ComparisonNode ge = new ComparisonNode("t3", "etl_month", ComparisonOperator.GE, "'2026-05-01'");
        ComparisonNode c3 = new ComparisonNode("t3", "c3", ComparisonOperator.EQ, "'28'");
        ComparisonNode le = new ComparisonNode("t3", "etl_month", ComparisonOperator.LE, "'2027-06-01'");
        ComparisonNode c4 = new ComparisonNode("t3", "c4", ComparisonOperator.EQ, "'28'");
        ComparisonNode eq = new ComparisonNode("t3", "etl_month", ComparisonOperator.EQ, "'2027-07-01'");
        ComparisonNode c5 = new ComparisonNode("t3", "c5", ComparisonOperator.EQ, "'28'");

        ConditionNode u1 = ConditionNode.minUnit("t3", "tb3", Arrays.asList(ge, c3));
        ConditionNode u2 = ConditionNode.minUnit("t3", "tb3", Arrays.asList(le, c4));
        ConditionNode u3 = ConditionNode.minUnit("t3", "tb3", Arrays.asList(eq, c5));

        List<ConditionNode> merged = MinUnitMergeHelper.mergeCompatible(
                Arrays.asList(u1, u2, u3), new ColumnConditionMerger());

        Assert.assertEquals(2, merged.size());
    }

    @Test
    public void parserKeepsSeparateSnapshotsBeforeValidate() {
        List<TableInfo> tableInfos = Arrays.asList(
                new TableInfo("tb3", "t3", "etl_month", "VARCHAR", "字段etl_month"),
                new TableInfo("tb3", "t3", "c3", "VARCHAR", "字段c3"),
                new TableInfo("tb3", "t3", "c4", "VARCHAR", "字段c4"),
                new TableInfo("tb3", "t3", "c5", "VARCHAR", "字段c5")
        );
        String condition = "(t3.etl_month >= '2026-05-01' and t3.c3 = '28') and (t3.etl_month <= '2027-06-01' and t3.c4 = '28') and (t3.etl_month = '2027-07-01' and t3.c5 = '28')";
        ConditionParser parser = new ConditionParser(tableInfos);
        ConditionNode parsed = parser.parse(condition);
        Assert.assertEquals(2, countMinUnits(parsed));
    }

    @Test
    public void endToEndDifferentEtlMonthSnapshots() {
        List<TableInfo> tableInfos = Arrays.asList(
                new TableInfo("tb3", "t3", "etl_month", "VARCHAR", "字段etl_month"),
                new TableInfo("tb3", "t3", "c3", "VARCHAR", "字段c3"),
                new TableInfo("tb3", "t3", "c4", "VARCHAR", "字段c4"),
                new TableInfo("tb3", "t3", "c5", "VARCHAR", "字段c5")
        );
        SqlQueryService service = new SqlQueryService(tableInfos);
        String condition = "(t3.etl_month >= '2026-05-01' and t3.c3 = '28') and (t3.etl_month <= '2027-06-01' and t3.c4 = '28') and (t3.etl_month = '2027-07-01' and t3.c5 = '28')";
        SqlBuildResult result = service.build(condition);
        Assert.assertEquals(ConditionType.CONTRADICTION, result.getValidationResult().getConditionType());
        Assert.assertNull(result.getQuerySql());
    }

    private int countMinUnits(ConditionNode node) {
        if (node.getType() == ConditionNode.NodeType.MIN_UNIT) {
            return 1;
        }
        int count = 0;
        for (ConditionNode child : node.getChildren()) {
            count += countMinUnits(child);
        }
        return count;
    }
}
