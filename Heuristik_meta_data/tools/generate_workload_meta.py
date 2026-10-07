#!/usr/bin/env python3
"""
Workload generator for the metaheuristic experiments (GSA vs BWO).

Differences from generate_workload.py (HRRN version):
  * Sizes go beyond the 1000 rows that exist in the GoCJ workbook (up to 10,000).
  * Three consecutive "waves" (batches) are produced: data/wave1, wave2, wave3.
      - wave 1 : ALL sizes (100..1000 step 100, 2000, 3000, 4000, 5000, 7500, 10000)
      - wave 2 : random subset of those sizes (stratified: --k-small from the 10 sizes <= 1000,
                 --k-large from the 6 sizes > 1000)
      - wave 3 : another independent random subset, same rule
    data/pilot holds extra instances (own seeds) for parameter tuning only.
    The chosen sizes are written to data/wave_manifest.csv.

How `length` (MI) is produced
  The GoCJ sheets use  =RAND()  +  =VLOOKUP(rand, $B$1:$C$50, 2)  on a 50-row table
  (every row has probability 0.02). That is exactly "pick one of the 50 lengths uniformly".
  We read that 50-row table from the workbook (columns B/C) and repeat the same draw with a
  seeded RNG, so any N is possible and the CSV is frozen (the xlsx re-rolls on every open).

All other columns (arrival, deadline, priority, sizes, ram_util) use the same rules as the
HRRN generator, so the existing DatasetLoader / TaskSpec read these files unchanged.

Usage:
    python tools/generate_workload_meta.py --xlsx GoCJ_DataSet_Monte_Carlo.xlsx
"""
import argparse
import csv
import os
import random
from collections import Counter

from openpyxl import load_workbook

DEFAULT_SIZES = list(range(100, 1001, 100)) + [2000, 3000, 4000, 5000, 7500, 10000]

HEADER = ["id", "length", "file_size_mb", "output_size_mb",
          "arrival_time", "deadline", "priority", "ram_util", "size_class"]
FILE_MB = {"small": (5, 50), "medium": (50, 300), "large": (300, 1000)}


def size_class(length):
    if length <= 27_500:
        return "small"
    if length <= 150_000:
        return "medium"
    return "large"


def read_length_table(xlsx):
    """50-row Monte Carlo table: (cumulative probability, length in MI)."""
    wb = load_workbook(xlsx, read_only=True, data_only=True)
    ws = wb["Cloudlets_Jobs_1000"]
    table = []
    for r in ws.iter_rows(min_row=1, max_row=50, values_only=True):
        if isinstance(r[1], (int, float)) and isinstance(r[2], (int, float)):
            table.append((float(r[1]), int(r[2])))
    if len(table) != 50:
        raise SystemExit(f"expected 50 rows in the Monte Carlo table, found {len(table)}")
    return table


def draw_length(table, rng):
    """Same as VLOOKUP(RAND(), B:C, 2) with approximate match."""
    u = rng.random()
    chosen = table[0][1]
    for cum, length in table:
        if cum <= u:
            chosen = length
        else:
            break
    return chosen


def generate(n, table, rng, load, capacity_mips, ref_mips):
    lengths = [draw_length(table, rng) for _ in range(n)]
    mean_len = sum(lengths) / n
    mean_interarrival = mean_len / (load * capacity_mips)   # Poisson arrivals, offered load = `load`
    t = 0.0
    rows = []
    for i, length in enumerate(lengths):
        if i > 0:
            t += rng.expovariate(1.0 / mean_interarrival)
        cls = size_class(length)
        lo, hi = FILE_MB[cls]
        file_mb = rng.randint(lo, hi)
        out_mb = max(1, int(file_mb * rng.uniform(0.1, 0.5)))
        ram_util = 0.12 if rng.random() < 0.3 else 0.05
        deadline = t + rng.uniform(2.0, 6.0) * (length / ref_mips)
        priority = rng.choices([1, 2, 3], weights=[0.2, 0.5, 0.3])[0]  # 1 = highest
        rows.append([i, length, file_mb, out_mb, round(t, 2), round(deadline, 2),
                     priority, ram_util, cls])
    return rows


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--xlsx", required=True)
    ap.add_argument("--sizes", type=int, nargs="+", default=DEFAULT_SIZES)
    ap.add_argument("--k-small", type=int, default=5, help="sizes picked from those <= 1000 in wave 2/3")
    ap.add_argument("--k-large", type=int, default=3, help="sizes picked from those > 1000 in wave 2/3")
    ap.add_argument("--pilot-sizes", type=int, nargs="*", default=[500, 2000],
                    help="extra instances in data/pilot (own seed) used only for parameter tuning")
    ap.add_argument("--select-seed", type=int, default=2026, help="seed for choosing the wave 2/3 sizes")
    ap.add_argument("--out", default="data")
    ap.add_argument("--seed", type=int, default=42, help="base seed; wave w and size n use seed + 100000*w + n")
    ap.add_argument("--load", type=float, default=2.0, help="offered load vs cluster capacity")
    ap.add_argument("--capacity-mips", type=float, default=92_000,
                    help="sum over VMs of mips*vCPU (12x2x1000 + 6x4x1500 + 2x8x2000)")
    ap.add_argument("--ref-mips", type=float, default=1_000, help="slowest VM MIPS, for deadlines")
    args = ap.parse_args()

    table = read_length_table(args.xlsx)
    sizes = sorted(args.sizes)
    small = [n for n in sizes if n <= 1000]
    large = [n for n in sizes if n > 1000]

    # wave -> list of sizes (wave 1 = everything, waves 2/3 = stratified random subsets)
    plan = {1: sizes}
    for w in (2, 3):
        rs = random.Random(args.select_seed + w)
        plan[w] = sorted(rs.sample(small, min(args.k_small, len(small)))
                         + rs.sample(large, min(args.k_large, len(large))))

    os.makedirs(args.out, exist_ok=True)
    with open(os.path.join(args.out, "wave_manifest.csv"), "w", newline="") as f:
        cw = csv.writer(f)
        cw.writerow(["wave", "run_order", "tasks", "file"])
        for w, ns in plan.items():
            for k, n in enumerate(ns, 1):
                cw.writerow([w, k, n, f"wave{w}/workload_{n}.csv"])

    summary = []
    for w, ns in plan.items():
        wdir = os.path.join(args.out, f"wave{w}")
        os.makedirs(wdir, exist_ok=True)
        for n in ns:
            rng = random.Random(args.seed + 100_000 * w + n)
            rows = generate(n, table, rng, args.load, args.capacity_mips, args.ref_mips)
            with open(os.path.join(wdir, f"workload_{n}.csv"), "w", newline="") as f:
                cw = csv.writer(f)
                cw.writerow(HEADER)
                cw.writerows(rows)
            cls = Counter(r[8] for r in rows)
            lengths = [r[1] for r in rows]
            summary.append([w, n, round(sum(lengths) / n), min(lengths), max(lengths),
                            cls["small"], cls["medium"], cls["large"],
                            round(rows[-1][4], 1), sum(r[2] for r in rows)])
            print(f"wave{w} n={n:>5}: mean {sum(lengths)/n:>9,.0f} MI | "
                  f"S/M/L {cls['small']}/{cls['medium']}/{cls['large']} | span {rows[-1][4]:,.0f}s")
        print(f"-- wave{w}: {len(ns)} instances: {ns}")

    # pilot instances: independent seeds, never part of the reported waves
    pdir = os.path.join(args.out, "pilot")
    os.makedirs(pdir, exist_ok=True)
    for n in args.pilot_sizes:
        rng = random.Random(args.seed + 900_000 + n)
        rows = generate(n, table, rng, args.load, args.capacity_mips, args.ref_mips)
        with open(os.path.join(pdir, f"workload_{n}.csv"), "w", newline="") as f:
            cw = csv.writer(f)
            cw.writerow(HEADER)
            cw.writerows(rows)
        print(f"pilot n={n}: written")

    with open(os.path.join(args.out, "dataset_summary.csv"), "w", newline="") as f:
        cw = csv.writer(f)
        cw.writerow(["wave", "tasks", "mean_length_mi", "min_length_mi", "max_length_mi",
                     "n_small", "n_medium", "n_large", "arrival_span_s", "total_file_mb"])
        cw.writerows(summary)


if __name__ == "__main__":
    main()
