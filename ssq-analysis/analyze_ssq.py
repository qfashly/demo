#!/usr/bin/env python3
"""双色球历史统计与多策略选号（娱乐参考，不提高中奖概率）。"""
from __future__ import annotations

import argparse
import csv
import collections
import json
import random
import re
import statistics
import urllib.request
from pathlib import Path

PRIMES = {2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31}


def fetch_500(limit: int = 300):
    url = f"https://datachart.500.com/ssq/history/newinc/history.php?limit={limit}&sort=0"
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    html = urllib.request.urlopen(req, timeout=30).read().decode("gb2312", errors="ignore")
    draws = []
    rows = re.findall(r'<tr[^>]*class="t_tr1"[^>]*>(.*?)</tr>', html, re.S)
    for row in rows:
        row = re.sub(r"<!--.*?-->", "", row, flags=re.S)
        tds = re.findall(r"<td[^>]*>(.*?)</td>", row, re.S)
        texts = [re.sub(r"<.*?>", "", t).strip() for t in tds]
        if len(texts) < 9:
            continue
        issue = texts[0]
        if not re.fullmatch(r"\d{5}", issue):
            issue = next((t for t in texts if re.fullmatch(r"\d{5}", t)), None)
            if not issue:
                continue
            texts = texts[texts.index(issue) :]
        reds = [int(texts[i]) for i in range(1, 7)]
        blue = int(texts[7])
        date = texts[-1] if re.fullmatch(r"\d{4}-\d{2}-\d{2}", texts[-1]) else ""
        draws.append({"code": "20" + issue, "date": date, "red": sorted(reds), "blue": blue})
    draws.sort(key=lambda x: x["code"], reverse=True)
    return draws


def odd_even(reds):
    o = sum(1 for x in reds if x % 2 == 1)
    return o, 6 - o


def big_small(reds):
    b = sum(1 for x in reds if x >= 17)
    return b, 6 - b


def zone(reds):
    return (
        sum(1 for x in reds if 1 <= x <= 11),
        sum(1 for x in reds if 12 <= x <= 22),
        sum(1 for x in reds if 23 <= x <= 33),
    )


def span(reds):
    return max(reds) - min(reds)


def sumv(reds):
    return sum(reds)


def consecutives(reds):
    s = sorted(reds)
    return sum(1 for a, b in zip(s, s[1:]) if b - a == 1)


def overlap(a, b):
    return len(set(a) & set(b))


def neighbors(prev, curr):
    return sum(1 for x in curr if any(abs(x - y) == 1 for y in prev))


def freq(window, kind="red"):
    c = collections.Counter()
    for d in window:
        if kind == "red":
            c.update(d["red"])
        else:
            c[d["blue"]] += 1
    return c


def omission(draws, kind="red"):
    numbers = range(1, 34) if kind == "red" else range(1, 17)
    omit = {n: None for n in numbers}
    for i, d in enumerate(draws):
        vals = d["red"] if kind == "red" else [d["blue"]]
        for n in vals:
            if omit[n] is None:
                omit[n] = i
    for n in omit:
        if omit[n] is None:
            omit[n] = len(draws)
    return omit


def avg_omission(draws_oldest, kind="red"):
    numbers = range(1, 34) if kind == "red" else range(1, 17)
    last = {n: -1 for n in numbers}
    gaps = {n: [] for n in numbers}
    for i, d in enumerate(draws_oldest):
        vals = d["red"] if kind == "red" else [d["blue"]]
        for n in vals:
            gaps[n].append(i - last[n] - 1)
            last[n] = i
    return {n: (sum(gaps[n]) / len(gaps[n]) if gaps[n] else 0) for n in gaps}


def analyze(draws, seed: int, tickets_n: int = 10):
    random.seed(seed)
    W30, W50, W100 = draws[:30], draws[:50], draws[:100]
    latest = draws[0]
    f30, f50, f100 = freq(W30), freq(W50), freq(W100)
    b30, b50, b100 = freq(W30, "blue"), freq(W50, "blue"), freq(W100, "blue")
    red_omit, blue_omit = omission(draws), omission(draws, "blue")
    red_avg = avg_omission(list(reversed(draws)))
    blue_avg = avg_omission(list(reversed(draws)), "blue")
    ranked50 = sorted(range(1, 34), key=lambda n: (-f50[n], n))
    hot, warm, cold = ranked50[:11], ranked50[11:22], ranked50[22:]
    blue_hot = sorted(range(1, 17), key=lambda n: (-b50[n], n))[:5]
    red_omit_sorted = sorted(range(1, 34), key=lambda n: (-red_omit[n], n))
    blue_omit_sorted = sorted(range(1, 17), key=lambda n: (-blue_omit[n], n))
    oe_dist = collections.Counter(odd_even(d["red"]) for d in W100)
    bs_dist = collections.Counter(big_small(d["red"]) for d in W100)
    zn_dist = collections.Counter(zone(d["red"]) for d in W100)
    sum_vals = [sumv(d["red"]) for d in W100]
    span_vals = [span(d["red"]) for d in W100]
    con_dist = collections.Counter(consecutives(d["red"]) for d in W100)
    sv, spv = sorted(sum_vals), sorted(span_vals)
    sum_lo, sum_hi = sv[len(sv) // 10], sv[len(sv) * 9 // 10]
    span_lo, span_hi = spv[len(spv) // 10], spv[len(spv) * 9 // 10]

    def red_score(n, mode="balanced"):
        freq_score = f50[n] / max(1, max(f50.values()))
        omit_ratio = red_omit[n] / max(1.0, red_avg[n] if red_avg[n] > 0 else 5.5)
        omit_score = min(omit_ratio / 2.0, 1.5)
        recent = 1.0 if any(n in d["red"] for d in draws[:5]) else 0.0
        if mode == "hot":
            return 0.7 * freq_score + 0.15 * omit_score + 0.15 * recent
        if mode == "cold":
            return 0.15 * freq_score + 0.75 * omit_score + 0.1 * (1 - recent)
        return 0.45 * freq_score + 0.40 * omit_score + 0.15 * recent

    def blue_score(n, mode="balanced"):
        freq_score = b50[n] / max(1, max(b50.values()))
        omit_ratio = blue_omit[n] / max(1.0, blue_avg[n] if blue_avg[n] > 0 else 15.0)
        omit_score = min(omit_ratio / 2.0, 1.5)
        recent = 1.0 if any(d["blue"] == n for d in draws[:5]) else 0.0
        if mode == "hot":
            return 0.75 * freq_score + 0.15 * omit_score + 0.1 * recent
        if mode == "cold":
            return 0.15 * freq_score + 0.75 * omit_score + 0.1 * (1 - recent)
        return 0.45 * freq_score + 0.45 * omit_score + 0.1 * recent

    def valid_structure(reds, allow_extreme=False):
        if len(set(reds)) != 6:
            return False
        oe, bs, zn = odd_even(reds), big_small(reds), zone(reds)
        s, sp, cn = sumv(reds), span(reds), consecutives(reds)
        if cn > 2 or set(reds) == set(latest["red"]):
            return False
        if allow_extreme:
            return True
        if oe not in {(2, 4), (3, 3), (4, 2), (1, 5), (5, 1)}:
            return False
        if bs not in {(2, 4), (3, 3), (4, 2), (5, 1), (1, 5)}:
            return False
        if s < sum_lo - 15 or s > sum_hi + 15:
            return False
        if sp < max(12, span_lo - 6) or sp > span_hi + 6:
            return False
        if max(zn) >= 5 or (0 in zn and max(zn) >= 4):
            return False
        return True

    def weighted_sample(scores, k):
        pool = [(n, max(scores[n], 1e-6)) for n in scores]
        chosen = []
        for _ in range(k):
            total = sum(w for _, w in pool) or 1
            r = random.random()
            acc = 0
            picked = len(pool) - 1
            for i, (n, w) in enumerate(pool):
                acc += w / total
                if r <= acc:
                    picked = i
                    break
            n, _ = pool.pop(picked)
            chosen.append(n)
        return sorted(chosen)

    def pick_blue(mode):
        scores = {n: blue_score(n, mode) for n in range(1, 17)}
        total = sum(max(w, 1e-6) for w in scores.values())
        r = random.random()
        acc = 0
        for n, w in scores.items():
            acc += max(w, 1e-6) / total
            if r <= acc:
                return n
        return max(scores, key=scores.get)

    def generate_ticket(mode, prefer=None):
        scores = {n: red_score(n, mode) for n in range(1, 34)}
        if prefer:
            for n in prefer:
                scores[n] = scores.get(n, 0) + 0.3
        for loose in (False, True):
            for _ in range(1000):
                reds = weighted_sample(scores, 6)
                if mode == "balanced":
                    h = sum(1 for x in reds if x in hot)
                    c = sum(1 for x in reds if x in cold)
                    if h >= 5 or c >= 5:
                        continue
                if not valid_structure(reds, allow_extreme=loose):
                    continue
                if overlap(reds, latest["red"]) > 3:
                    continue
                blue_mode = "hot" if mode == "hot" else ("cold" if mode == "cold" else "balanced")
                return reds, pick_blue(blue_mode)
        return weighted_sample({n: red_score(n, mode) for n in range(1, 34)}, 6), pick_blue("balanced")

    strategies = [
        ("热号主攻", "hot", hot[:10]),
        ("热号+结构", "hot", hot[:6] + warm[:4]),
        ("冷热均衡A", "balanced", None),
        ("冷热均衡B", "balanced", hot[:3] + warm[:3] + cold[:3]),
        ("冷号回补", "cold", red_omit_sorted[:10]),
        ("遗漏回补+温号", "cold", warm[:6] + red_omit_sorted[:6]),
        ("分区均衡", "balanced", None),
        ("邻号/重号控制", "balanced", None),
        ("蓝热红均", "balanced", None),
        ("综合加权", "balanced", hot[:4] + cold[:3] + warm[:3]),
    ][:tickets_n]

    tickets = []
    used = set()
    for name, mode, prefer in strategies:
        for attempt in range(80):
            reds, blue = generate_ticket(mode, prefer)
            if name.startswith("分区"):
                z = zone(reds)
                if min(z) == 0 or max(z) - min(z) > 2:
                    if attempt < 60:
                        continue
            if name.startswith("邻号"):
                ov, nb = overlap(reds, latest["red"]), neighbors(latest["red"], reds)
                if not (1 <= ov <= 2 and 1 <= nb <= 3):
                    if attempt < 60:
                        continue
            if name.startswith("蓝热"):
                blue = max(range(1, 17), key=lambda n: blue_score(n, "hot"))
            key = (tuple(reds), blue)
            if key in used:
                continue
            if any(len(set(reds) & set(t["red"])) >= 5 for t in tickets) and attempt < 50:
                continue
            used.add(key)
            tickets.append(
                {
                    "strategy": name,
                    "red": reds,
                    "blue": blue,
                    "odd_even": odd_even(reds),
                    "big_small": big_small(reds),
                    "zones": zone(reds),
                    "sum": sumv(reds),
                    "span": span(reds),
                    "consec": consecutives(reds),
                    "overlap_last": overlap(reds, latest["red"]),
                    "neighbors_last": neighbors(latest["red"], reds),
                }
            )
            break

    stats = {
        "latest": latest,
        "count": len(draws),
        "hot": hot,
        "warm": warm,
        "cold": cold,
        "blue_hot": blue_hot,
        "red_omit_top": red_omit_sorted[:10],
        "blue_omit_top": blue_omit_sorted[:5],
        "f30": dict(f30),
        "f50": dict(f50),
        "f100": dict(f100),
        "b30": dict(b30),
        "b50": dict(b50),
        "b100": dict(b100),
        "red_omit": red_omit,
        "blue_omit": blue_omit,
        "red_avg": red_avg,
        "blue_avg": blue_avg,
        "oe_dist": {f"{a}:{b}": c for (a, b), c in oe_dist.most_common()},
        "bs_dist": {f"{a}:{b}": c for (a, b), c in bs_dist.most_common()},
        "zn_dist": {f"{a}-{b}-{c}": n for (a, b, c), n in zn_dist.most_common()},
        "sum_p10_p90": [sum_lo, sum_hi],
        "sum_mean": statistics.mean(sum_vals),
        "span_p10_p90": [span_lo, span_hi],
        "span_mean": statistics.mean(span_vals),
        "con_dist": dict(sorted(con_dist.items())),
    }
    return stats, tickets


def render_report(draws, stats, tickets, next_code=None, next_time=None) -> str:
    latest = stats["latest"]
    lines = [
        "# 双色球娱乐分析报告",
        "",
        f"- 数据来源：500.com 历史开奖（仓库内未找到用户附件，改用公开数据）",
        f"- 样本期数：{stats['count']} 期（最新 {latest['code']} / {latest['date']} → 最早 {draws[-1]['code']} / {draws[-1]['date']}）",
        f"- 预测目标：下一期" + (f" {next_code}" if next_code else "") + (f"（约 {next_time}）" if next_time else ""),
        f"- 上期开奖：红球 {' '.join(f'{x:02d}' for x in latest['red'])} + 蓝球 {latest['blue']:02d}",
        "",
        "> 声明：双色球各期独立随机，任何统计都不能提高中奖概率；以下仅供回顾与娱乐参考，不构成购彩建议。",
        "",
        "## 1. 红球频率（近 30 / 50 / 100 期）",
        "",
        "|号码|近30|近50|近100|当前遗漏|平均遗漏|分类(近50)|",
        "|---:|---:|---:|---:|---:|---:|---|",
    ]
    for n in range(1, 34):
        tag = "热" if n in stats["hot"] else ("冷" if n in stats["cold"] else "温")
        lines.append(
            f"|{n:02d}|{stats['f30'].get(n,0)}|{stats['f50'].get(n,0)}|{stats['f100'].get(n,0)}|{stats['red_omit'][n]}|{stats['red_avg'][n]:.1f}|{tag}|"
        )
    lines += [
        "",
        f"- 热号(近50 TOP11)：{', '.join(f'{n:02d}' for n in stats['hot'])}",
        f"- 冷号(近50 BOTTOM11)：{', '.join(f'{n:02d}' for n in stats['cold'])}",
        f"- 遗漏偏高候选：{', '.join(f'{n:02d}({stats['red_omit'][n]})' for n in stats['red_omit_top'])}",
        "",
        "## 2. 蓝球专项",
        "",
        "|号码|近30|近50|近100|当前遗漏|平均遗漏|",
        "|---:|---:|---:|---:|---:|---:|",
    ]
    for n in range(1, 17):
        lines.append(
            f"|{n:02d}|{stats['b30'].get(n,0)}|{stats['b50'].get(n,0)}|{stats['b100'].get(n,0)}|{stats['blue_omit'][n]}|{stats['blue_avg'][n]:.1f}|"
        )
    lines += [
        "",
        f"- 蓝热(近50)：{', '.join(f'{n:02d}' for n in stats['blue_hot'])}",
        f"- 蓝冷/高遗漏：{', '.join(f'{n:02d}(遗漏{stats['blue_omit'][n]})' for n in stats['blue_omit_top'])}",
        "",
        "## 3. 形态结构（近100期）",
        "",
        f"- 奇偶比 TOP：{', '.join(f'{k}×{v}' for k,v in list(stats['oe_dist'].items())[:5])}",
        f"- 大小比 TOP：{', '.join(f'{k}×{v}' for k,v in list(stats['bs_dist'].items())[:5])}",
        f"- 分区 TOP：{', '.join(f'{k}×{v}' for k,v in list(stats['zn_dist'].items())[:5])}",
        f"- 和值 P10–P90：{stats['sum_p10_p90'][0]}–{stats['sum_p10_p90'][1]}（均值 {stats['sum_mean']:.1f}）",
        f"- 跨度 P10–P90：{stats['span_p10_p90'][0]}–{stats['span_p10_p90'][1]}（均值 {stats['span_mean']:.1f}）",
        f"- 连号个数分布：{stats['con_dist']}",
        "",
        "## 4. 推荐 10 注（多策略）",
        "",
        "|注|策略|红球|蓝球|奇偶|大小|分区|和值|跨度|重号|",
        "|---:|---|---|---:|---|---|---|---:|---:|---:|",
    ]
    for i, t in enumerate(tickets, 1):
        reds = " ".join(f"{x:02d}" for x in t["red"])
        oe = f"{t['odd_even'][0]}:{t['odd_even'][1]}"
        bs = f"{t['big_small'][0]}:{t['big_small'][1]}"
        zn = f"{t['zones'][0]}-{t['zones'][1]}-{t['zones'][2]}"
        lines.append(
            f"|{i}|{t['strategy']}|{reds}|{t['blue']:02d}|{oe}|{bs}|{zn}|{t['sum']}|{t['span']}|{t['overlap_last']}|"
        )
    lines += ["", "### 纯号码列表", ""]
    for i, t in enumerate(tickets, 1):
        lines.append(f"{i}. {' '.join(f'{x:02d}' for x in t['red'])} + {t['blue']:02d}  （{t['strategy']}）")
    lines.append("")
    return "\n".join(lines)


def main():
    ap = argparse.ArgumentParser(description="双色球娱乐统计分析")
    ap.add_argument("--limit", type=int, default=300)
    ap.add_argument("--seed", type=int, default=2026084)
    ap.add_argument("--out", type=Path, default=Path("ssq-analysis"))
    args = ap.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    draws = fetch_500(args.limit)
    stats, tickets = analyze(draws, seed=args.seed)
    next_code = next_time = None
    try:
        req = urllib.request.Request(
            "https://api.huiniao.top/interface/home/lotteryHistory?type=ssq&page=1&page_size=1",
            headers={"User-Agent": "Mozilla/5.0"},
        )
        meta = json.loads(urllib.request.urlopen(req, timeout=20).read().decode())["data"]["last"]
        next_code, next_time = meta.get("next_code"), meta.get("next_open_time")
    except Exception:
        pass
    report = render_report(draws, stats, tickets, next_code, next_time)
    (args.out / "REPORT.md").write_text(report, encoding="utf-8")
    (args.out / "tickets.json").write_text(json.dumps(tickets, ensure_ascii=False, indent=2), encoding="utf-8")
    with open(args.out / "history.csv", "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["code", "date", "r1", "r2", "r3", "r4", "r5", "r6", "blue"])
        for d in draws:
            w.writerow([d["code"], d["date"], *d["red"], d["blue"]])
    (args.out / "history.json").write_text(json.dumps(draws, ensure_ascii=False, indent=2), encoding="utf-8")
    print(report)


if __name__ == "__main__":
    main()
