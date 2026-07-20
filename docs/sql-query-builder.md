# SQL 条件查询组装器

将用户输入的多表筛选条件（字符串）解析为结构化条件树，完成合法性校验后，组装成以企业主表 `t_com_entprise_cid` 为驱动表、按 `cid` 关联各特征表子查询（`jtb1`、`jtb2`…）的 **count SQL** 与 **query SQL**。

## 整体流程

```
用户 condition 字符串
        │
        ▼
┌───────────────────┐
│  ConditionParser  │  词法/语法分析 → 条件 AST → 归并为 MIN_UNIT
└─────────┬─────────┘
          │
          ▼
┌───────────────────┐
│ ConditionValidator│  列条件合并、同表 MIN_UNIT 处理、矛盾检测
└─────────┬─────────┘
          │
          ▼
┌───────────────────┐
│  SqlQueryBuilder  │  生成 jtb 子查询 + 主查询 SELECT/JOIN
└─────────┬─────────┘
          │
          ▼
   SqlBuildResult (countSql / querySql)
```

入口类：`com.chenxy.demo.sql.SqlQueryService`

## 核心概念

### 1. 条件 AST（`ConditionNode`）

解析后的条件是一棵树，节点类型：

| 类型 | 含义 |
|------|------|
| `AND` / `OR` | 逻辑组合 |
| `MIN_UNIT` | **最小查询单元**：同一表别名下的一组比较条件，必须含 `etl_month` |
| `CROSS_TABLE` | 跨表比较，如 `t1.c1 = t2.c2`，放在主查询 WHERE |
| `RAW` | 函数/复杂表达式，原样拼入 SQL |
| `COMPARISON` | 解析中间态，最终会包装为 MIN_UNIT 或 CROSS_TABLE |

### 2. MIN_UNIT（最小查询单元）

业务上，每个特征表的一次「分区快照 + 业务过滤」对应一个 MIN_UNIT，最终映射为一个 **jtb 子查询**。

示例：

```sql
( t1.etl_month = '2026-07-01' and t1.soc_pay_per_num_2m >= 0 )
and t2.etl_month = ( select max(etl_month) from tfedbqa.t_chara_op_cost_water )
```

解析为两个 MIN_UNIT：

- `t1`：etl_month 字面量 + 业务字段 `soc_pay_per_num_2m`
- `t2`：仅 etl_month 子查询（无业务字段过滤）

### 3. etl_month 特殊规则

`etl_month` 是分区字段，同一物理表在 **AND** 下出现多组括号条件时：

1. **矛盾检测**（`SameTableEtlMonthContradictionChecker`）  
   合并各 MIN_UNIT 的 etl_month 约束后，若无法用**一个**分区月份同时满足（如 `= '2026-07-01'` 与 `>= '2026-08-01'`），判定为 `CONTRADICTION`。

2. **同表 MIN_UNIT 合并**（`SameTableMinUnitProcessor`）  
   - etl_month 约束**完全相同** → 合并为一个 MIN_UNIT（对应一个 jtb）  
   - 约束不同但**不矛盾**（如 `>= '2025-05-01'` 与 `<= '2026-06-01'`）→ 保持多个 MIN_UNIT，各自生成 jtb，**各自保留 etl_month**  
   - **矛盾** → 不合并，校验阶段返回 `CONTRADICTION`

3. **列条件合并**（`ColumnConditionMerger`）  
   同一 MIN_UNIT 内重复的 etl_month 条件会合并（如两个 `=` 合成 `IN`，`>=` + `<=` 合成 `BETWEEN`）。  
   **子查询/函数表达式**（`OperandType.EXPRESSION`）不参与字面量合并，原样保留。

4. **静态矛盾分析限制**  
   含子查询的 etl_month（如 `= (select max(...))`）无法与字面量做静态比较，不参与字面量矛盾判定。

### 4. jtb 子查询与输出列

`SqlQueryBuilder` 为每个 MIN_UNIT 生成 `jtbN` 子查询：

```sql
join (
    select t1.cid, t1.soc_pay_per_num_2m
    from schema.t_chara_op_cost_insu t1
    where t1.etl_month = '2026-07-01' and t1.soc_pay_per_num_2m >= 0
) jtb1 on t0.cid = jtb1.cid
```

**输出列规则**（`extractOutputColumns`）：

| MIN_UNIT 情况 | jtb / 主 SELECT 输出列 |
|---------------|------------------------|
| 有条件中出现的业务字段 | 仅这些业务字段 |
| 仅有 etl_month 条件 | `tableInfos` 中该表**除 cid 外**的全部字段（含 etl_month） |

主查询固定输出：`t0.cid`、`t0.ent_name`、`t0.uni_scid`，以及各 jtb 的业务输出列。

### 5. OR 分支

顶层或分支内的 `OR` 不展开为多个主查询，而是在单个 jtb 内用 **UNION** 合并分支子查询（详见 `SqlQueryBuilder.buildUnionJoinUnit`）。

### 6. SQL 结构优化（`SqlQueryOptimizer`）

校验通过后、生成 SQL 前，对条件 AST 做结构优化：

| 优化项 | 说明 |
|--------|------|
| 同表 MIN_UNIT 合并 | etl_month 兼容的多个 MIN_UNIT 合并为一个 jtb（如 `<= '2026-07-01'` + `>= '2026-03-01'`） |

**不合并**的情况：etl_month 矛盾（如 `= '2026-07-01'` 与 `>= '2026-08-01'`）→ 返回 `CONTRADICTION`。

实现类：`optimizer.CompatibleMinUnitMergeOptimizer`（基于 `MinUnitMergeHelper`）。

## 包结构

```
com.chenxy.demo.sql
├── SqlQueryService          # 入口：parse → validate → build
├── parser/
│   ├── ConditionParser      # 字符串 → AST，归并 MIN_UNIT
│   └── ExpressionSanitizer    # 函数/子查询白名单校验
├── validator/
│   ├── ConditionValidator           # 优化 + 矛盾/恒真判定
│   ├── ColumnConditionMerger        # 同列多条件合并
│   ├── SameTableMinUnitProcessor    # 同表多 MIN_UNIT 合并策略
│   ├── SameTableEtlMonthContradictionChecker
│   └── ComparisonValueUtils         # 字面量比较工具
├── optimizer/
│   ├── SqlQueryOptimizer              # SQL 结构优化入口
│   └── CompatibleMinUnitMergeOptimizer # 合并 etl_month 兼容的同表 MIN_UNIT
│   └── SqlQueryBuilder      # AST → SQL
├── meta/
│   └── TableMetaRegistry    # tableInfos 元数据索引
└── model/                   # AST 节点、配置、结果 DTO
```

## 快速上手

```java
List<TableInfo> tableInfos = ...; // 表别名、字段元数据
SqlQueryConfig config = new SqlQueryConfig();
config.setMainTableSchema("tfedbqa");

SqlQueryService service = new SqlQueryService(tableInfos, config);
SqlBuildResult result = service.build(condition);

if (result.getValidationResult().isValid()) {
    String querySql = result.getQuerySql();
    String countSql = result.getCountSql();
}
```

## 校验结果类型（`ConditionType`）

| 类型 | 含义 | 是否生成 SQL |
|------|------|-------------|
| `SATISFIABLE` | 条件有效 | 是 |
| `CONTRADICTION` | 逻辑矛盾 | 否 |
| `TAUTOLOGY` | 恒真 | 否 |
| `SYNTAX_ERROR` | 语法/字段错误 | 否 |
| `UNKNOWN` | 暂无法判定 | 是（保守生成） |

## 常见输入示例

**多表 AND + t2 仅 etl_month 子查询**

```
( t1.etl_month = '2026-07-01' and t1.soc_pay_per_num_2m >= 0 )
and t2.etl_month = ( select max(etl_month) from tfedbqa.t_chara_op_cost_water )
```

**同表矛盾（应返回 CONTRADICTION）**

```
( t2.etl_month = '2026-07-01' and t2.c1 = 100 )
and ( t2.etl_month >= '2026-08-01' and t2.c2 = 1000 )
```

**etl_month 支持子查询/函数**

```
t1.etl_month = (select max(etl_month) from table1)
t1.etl_month = DATE('2026-05-01')
```

## 扩展阅读

- 单元测试：`src/test/java/com/chenxy/demo/sql/`
- 示例代码：`SqlQueryExample.java`
