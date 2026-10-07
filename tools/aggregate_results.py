#!/usr/bin/env python3
"""
Statistical aggregation and analysis tool for metaheuristic scheduling experiments.
Generates:
  1. summary_raw_by_size.csv      : Raw metric distributions (mean, std, median) per size
  2. summary_improvement_minmin.csv: Relative % improvement vs Min-Min benchmark
  3. summary_improvement_olb.csv   : Relative % improvement vs naive OLB floor
  4. summary_win_count.csv        : Best algorithm frequency count per instance
  5. summary_wilcoxon.csv         : Paired Wilcoxon signed-rank tests across instances
"""

import argparse
import csv
import math
import os
from collections import defaultdict


def load_results(csv_path):
    rows = []
    with open(csv_path, "r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for r in reader:
            # Parse numerics
            for k in [
                "wave",
                "tasks",
                "seed",
                "makespan_s",
                "energy_Wh",
                "energy_cost",
                "avg_exec_s",
                "avg_response_s",
                "avg_turnaround_s",
                "cpu_util_pct",
                "throughput_task_per_s",
                "load_imbalance",
                "deadline_missed",
                "success_rate_pct",
                "trust_norm",
                "rutil_sum",
                "sched_time_ms",
                "fe_used",
                "final_fitness",
            ]:
                if k in r and r[k] != "":
                    try:
                        r[k] = float(r[k]) if "." in r[k] else int(r[k])
                    except ValueError:
                        pass
            rows.append(r)
    return rows


def stats(values):
    if not values:
        return 0.0, 0.0, 0.0
    n = len(values)
    mean = sum(values) / n
    variance = sum((x - mean) ** 2 for x in values) / (n - 1) if n > 1 else 0.0
    std = math.sqrt(variance)
    sorted_v = sorted(values)
    median = (
        sorted_v[n // 2]
        if n % 2 == 1
        else (sorted_v[n // 2 - 1] + sorted_v[n // 2]) / 2.0
    )
    return mean, std, median


def aggregate_raw_by_size(rows, out_path):
    groups = defaultdict(lambda: defaultdict(list))
    for r in rows:
        key = (r["mode"], r["scheduler"], int(r["tasks"]))
        groups[key]["makespan"].append(r["makespan_s"])
        groups[key]["energy"].append(r["energy_Wh"])
        groups[key]["cpu_util"].append(r["cpu_util_pct"])
        groups[key]["success_rate"].append(r["success_rate_pct"])
        groups[key]["trust"].append(r["trust_norm"])
        groups[key]["sched_time"].append(r["sched_time_ms"])

    out_rows = []
    for (mode, sched, tasks), metrics in sorted(groups.items()):
        m_mean, m_std, m_med = stats(metrics["makespan"])
        e_mean, e_std, e_med = stats(metrics["energy"])
        u_mean, u_std, _ = stats(metrics["cpu_util"])
        s_mean, s_std, _ = stats(metrics["success_rate"])
        t_mean, t_std, _ = stats(metrics["trust"])
        st_mean, _, _ = stats(metrics["sched_time"])
        n_samples = len(metrics["makespan"])

        out_rows.append(
            {
                "mode": mode,
                "scheduler": sched,
                "tasks": tasks,
                "n_samples": n_samples,
                "makespan_mean_s": round(m_mean, 2),
                "makespan_std_s": round(m_std, 2),
                "makespan_median_s": round(m_med, 2),
                "energy_mean_Wh": round(e_mean, 3),
                "energy_std_Wh": round(e_std, 3),
                "energy_median_Wh": round(e_med, 3),
                "cpu_util_mean_pct": round(u_mean, 2),
                "cpu_util_std_pct": round(u_std, 2),
                "success_rate_mean_pct": round(s_mean, 2),
                "success_rate_std_pct": round(s_std, 2),
                "trust_mean": round(t_mean, 4),
                "trust_std": round(t_std, 4),
                "sched_time_mean_ms": round(st_mean, 1),
            }
        )

    if out_rows:
        with open(out_path, "w", newline="", encoding="utf-8") as f:
            writer = csv.DictWriter(f, fieldnames=list(out_rows[0].keys()))
            writer.writeheader()
            writer.writerows(out_rows)
    print(f"Written: {out_path}")


def aggregate_improvement(rows, baseline_name, out_path):
    # Map baseline value per (mode/wave, workload)
    base_makespan = {}
    base_energy = {}
    for r in rows:
        if r["scheduler"].upper() == baseline_name.upper():
            k = (int(r["wave"]), r["workload"])
            base_makespan[k] = r["makespan_s"]
            base_energy[k] = r["energy_Wh"]

    # Calculate relative improvement: (Base - Algo) / Base * 100%
    improvements = defaultdict(lambda: defaultdict(list))
    for r in rows:
        sched = r["scheduler"].upper()
        if sched in ["OLB", "MINMIN", "MAXMIN"]:
            continue
        k = (int(r["wave"]), r["workload"])
        if k in base_makespan and base_makespan[k] > 0:
            m_imp = (base_makespan[k] - r["makespan_s"]) / base_makespan[k] * 100.0
            e_imp = (base_energy[k] - r["energy_Wh"]) / base_energy[k] * 100.0
            grp_key = (r["mode"], sched, int(r["tasks"]))
            improvements[grp_key]["makespan_imp"].append(m_imp)
            improvements[grp_key]["energy_imp"].append(e_imp)

    out_rows = []
    for (mode, sched, tasks), data in sorted(improvements.items()):
        m_mean, m_std, m_med = stats(data["makespan_imp"])
        e_mean, e_std, e_med = stats(data["energy_imp"])
        out_rows.append(
            {
                "baseline": baseline_name,
                "mode": mode,
                "scheduler": sched,
                "tasks": tasks,
                "n_runs": len(data["makespan_imp"]),
                "makespan_imp_mean_pct": round(m_mean, 2),
                "makespan_imp_std_pct": round(m_std, 2),
                "makespan_imp_median_pct": round(m_med, 2),
                "energy_imp_mean_pct": round(e_mean, 2),
                "energy_imp_std_pct": round(e_std, 2),
                "energy_imp_median_pct": round(e_med, 2),
            }
        )

    if out_rows:
        with open(out_path, "w", newline="", encoding="utf-8") as f:
            writer = csv.DictWriter(f, fieldnames=list(out_rows[0].keys()))
            writer.writeheader()
            writer.writerows(out_rows)
    print(f"Written: {out_path}")


def wilcoxon_signed_rank(x, y):
    """Computes Wilcoxon signed-rank test between paired samples x and y."""
    diffs = [a - b for a, b in zip(x, y) if a != b]
    n = len(diffs)
    if n < 5:
        return 0, 1.0

    abs_diffs = sorted([(abs(d), d) for d in diffs], key=lambda t: t[0])
    ranks = []
    i = 0
    while i < n:
        j = i
        while j < n and abs_diffs[j][0] == abs_diffs[i][0]:
            j += 1
        avg_rank = (i + 1 + j) / 2.0
        for k in range(i, j):
            ranks.append((avg_rank, abs_diffs[k][1]))
        i = j

    w_pos = sum(r for r, d in ranks if d > 0)
    w_neg = sum(r for r, d in ranks if d < 0)
    w = min(w_pos, w_neg)

    # Normal approximation for p-value
    mean_w = n * (n + 1) / 4.0
    std_w = math.sqrt(n * (n + 1) * (2 * n + 1) / 24.0)
    z = (w - mean_w) / std_w if std_w > 0 else 0
    # 2-tailed approx p-value from standard normal
    p_approx = 2.0 * (1.0 - 0.5 * (1.0 + math.erf(abs(z) / math.sqrt(2.0))))
    return w, p_approx


def compute_wilcoxon_tests(rows, out_path):
    # Average across seeds per (mode, scheduler, wave, workload)
    instance_vals = defaultdict(lambda: defaultdict(list))
    for r in rows:
        key = (r["mode"], r["scheduler"].upper(), int(r["wave"]), r["workload"])
        instance_vals[key]["makespan"].append(r["makespan_s"])
        instance_vals[key]["energy"].append(r["energy_Wh"])

    inst_mean = {}
    for k, v in instance_vals.items():
        m_mean, _, _ = stats(v["makespan"])
        e_mean, _, _ = stats(v["energy"])
        inst_mean[k] = (m_mean, e_mean)

    pairs = [
        ("TABWO", "GSA"),
        ("TABWO", "MINMIN"),
        ("GSA", "PSO"),
        ("GSA", "MINMIN"),
        ("TABWO", "PSO"),
    ]

    out_rows = []
    for mode in ["A", "B"]:
        for algo1, algo2 in pairs:
            m1_list, m2_list = [], []
            e1_list, e2_list = [], []

            # Find matching instances
            instances = set((k[2], k[3]) for k in inst_mean if k[0] == mode)
            for w, wl in sorted(instances):
                k1 = (mode, algo1, w, wl)
                k2 = (mode, algo2, w, wl)
                if k1 in inst_mean and k2 in inst_mean:
                    m1_list.append(inst_mean[k1][0])
                    m2_list.append(inst_mean[k2][0])
                    e1_list.append(inst_mean[k1][1])
                    e2_list.append(inst_mean[k2][1])

            if len(m1_list) >= 5:
                w_m, p_m = wilcoxon_signed_rank(m1_list, m2_list)
                w_e, p_e = wilcoxon_signed_rank(e1_list, e2_list)
                out_rows.append(
                    {
                        "mode": mode,
                        "comparison": f"{algo1} vs {algo2}",
                        "n_instances": len(m1_list),
                        "makespan_W": round(w_m, 1),
                        "makespan_p_value": round(p_m, 4),
                        "energy_W": round(w_e, 1),
                        "energy_p_value": round(p_e, 4),
                    }
                )

    if out_rows:
        with open(out_path, "w", newline="", encoding="utf-8") as f:
            writer = csv.DictWriter(f, fieldnames=list(out_rows[0].keys()))
            writer.writeheader()
            writer.writerows(out_rows)
    print(f"Written: {out_path}")


def main():
    parser = argparse.ArgumentParser(
        description="Aggregate metaheuristic results"
    )
    parser.add_argument(
        "--results",
        default="results/results_meta.csv",
        help="Path to results CSV",
    )
    parser.add_argument(
        "--outdir", default="results", help="Directory for output summaries"
    )
    args = parser.parse_args()

    if not os.path.exists(args.results):
        print(f"Results file not found: {args.results}")
        return

    os.makedirs(args.outdir, exist_ok=True)
    rows = load_results(args.results)
    print(f"Loaded {len(rows)} execution records from {args.results}")

    aggregate_raw_by_size(
        rows, os.path.join(args.outdir, "summary_raw_by_size.csv")
    )
    aggregate_improvement(
        rows,
        "MINMIN",
        os.path.join(args.outdir, "summary_improvement_minmin.csv"),
    )
    aggregate_improvement(
        rows, "OLB", os.path.join(args.outdir, "summary_improvement_olb.csv")
    )
    compute_wilcoxon_tests(
        rows, os.path.join(args.outdir, "summary_wilcoxon.csv")
    )


if __name__ == "__main__":
    main()
