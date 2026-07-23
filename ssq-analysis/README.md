# 双色球娱乐分析

基于公开历史开奖数据，按频率/遗漏/形态结构做统计，并生成多策略 10 注参考号码。

**重要：开奖独立随机，分析结果不能提高中奖概率，仅供娱乐回顾。**

## 用法

```bash
python3 ssq-analysis/analyze_ssq.py --limit 300 --seed 2026084 --out ssq-analysis
```

输出：
- `history.csv` / `history.json`：历史开奖
- `REPORT.md`：分析报告
- `tickets.json`：10 注推荐明细
