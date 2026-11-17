#!/usr/bin/env python3
"""Generate clean SVG charts from experiment summaries and convergence logs."""

import csv
import math
import os
from collections import defaultdict

OUTDIR = os.path.join("results", "charts")

COLORS = {
    "MAXMIN": "#1b9e77",
    "MINMIN": "#2b8cbe",
    "OLB": "#7570b3",
    "TABWO": "#d95f02",
    "PSO": "#e7298a",
    "GSA": "#666666",
}

LABELS = {
    "MAXMIN": "Max-Min",
    "MINMIN": "Min-Min",
    "OLB": "OLB",
    "TABWO": "TABWO",
    "PSO": "PSO",
    "GSA": "GSA",
}

ORDER = ["MAXMIN", "MINMIN", "OLB", "TABWO", "PSO", "GSA"]


def load_raw_summary(path="results/summary_raw_by_size.csv"):
    data = {}
    with open(path, "r", encoding="utf-8") as f:
        for r in csv.DictReader(f):
            sched = r["scheduler"].upper()
            n = int(r["tasks"])
            data[(sched, n)] = {k: float(v) for k, v in r.items() if k not in ("scheduler", "tasks")}
    return data


def load_imp_minmin(path="results/summary_improvement_minmin.csv"):
    data = {}
    with open(path, "r", encoding="utf-8") as f:
        for r in csv.DictReader(f):
            sched = r["scheduler"].upper()
            n = int(r["tasks"])
            data[(sched, n)] = float(r["makespan_imp_mean_pct"])
    return data


def svg_header(w, h, title):
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}" '
        f'style="font-family: -apple-system, BlinkMacSystemFont, Segoe UI, Roboto, Arial, sans-serif; background: #ffffff;">\n'
        f'<rect width="{w}" height="{h}" fill="#ffffff" rx="6"/>\n'
        f'<text x="{w/2:.1f}" y="28" text-anchor="middle" font-size="16" font-weight="700" fill="#1f2937">{title}</text>\n'
    )


def nice_ticks(vmax, count=5, vmin=0.0):
    span = max(vmax - vmin, 1e-9)
    raw_step = span / count
    mag = 10 ** math.floor(math.log10(raw_step))
    norm = raw_step / mag
    if norm <= 1.5:
        step = 1 * mag
    elif norm <= 3:
        step = 2 * mag
    elif norm <= 7:
        step = 5 * mag
    else:
        step = 10 * mag
    start = math.floor(vmin / step) * step
    ticks = []
    v = start
    while v <= vmax + step * 0.5:
        ticks.append(v)
        v += step
    return ticks, ticks[0], ticks[-1]


def fmt_num(v):
    if abs(v) >= 1000:
        return f"{v:,.0f}"
    if abs(v) >= 10:
        return f"{v:.1f}"
    return f"{v:.2f}"


def plot_line_by_n(data, metric_key, title, ylabel, out_name, sizes=None):
    if sizes is None:
        sizes = [100, 200, 300, 500, 1000, 2000, 5000, 10000]
    w, h = 820, 440
    ml, mr, mt, mb = 78, 130, 54, 62
    pw, ph = w - ml - mr, h - mt - mb

    vals = [data[(s, n)][metric_key] for s in ORDER for n in sizes if (s, n) in data]
    ticks, ymin, ymax = nice_ticks(max(vals), 5, 0.0)

    svg = [svg_header(w, h, title)]
    # Grid & Y axis
    for tv in ticks:
        y = mt + ph - (tv - ymin) / (ymax - ymin) * ph
        svg.append(f'<line x1="{ml}" y1="{y:.1f}" x2="{ml+pw}" y2="{y:.1f}" stroke="#e5e7eb" stroke-width="1"/>')
        svg.append(f'<text x="{ml-8}" y="{y+4:.1f}" text-anchor="end" font-size="11" fill="#4b5563">{fmt_num(tv)}</text>')

    svg.append(f'<line x1="{ml}" y1="{mt}" x2="{ml}" y2="{mt+ph}" stroke="#9ca3af" stroke-width="1.2"/>')
    svg.append(f'<line x1="{ml}" y1="{mt+ph}" x2="{ml+pw}" y2="{mt+ph}" stroke="#9ca3af" stroke-width="1.2"/>')

    # X axis (equally spaced categories for readability across 100..10000)
    xs = [ml + i * pw / (len(sizes) - 1) for i in range(len(sizes))]
    for x, n in zip(xs, sizes):
        svg.append(f'<line x1="{x:.1f}" y1="{mt+ph}" x2="{x:.1f}" y2="{mt+ph+5}" stroke="#9ca3af"/>')
        svg.append(f'<text x="{x:.1f}" y="{mt+ph+20}" text-anchor="middle" font-size="11" fill="#4b5563">{n:,}</text>')

    svg.append(f'<text x="{ml+pw/2:.1f}" y="{h-14}" text-anchor="middle" font-size="12" font-weight="600" fill="#374151">Jumlah Task (N)</text>')
    svg.append(f'<text x="20" y="{mt+ph/2:.1f}" text-anchor="middle" font-size="12" font-weight="600" fill="#374151" transform="rotate(-90 20 {mt+ph/2:.1f})">{ylabel}</text>')

    # Lines
    for sched in ORDER:
        pts = []
        for x, n in zip(xs, sizes):
            if (sched, n) in data:
                val = data[(sched, n)][metric_key]
                y = mt + ph - (val - ymin) / (ymax - ymin) * ph
                pts.append((x, y, val))
        if not pts:
            continue
        pstr = " ".join(f"{x:.1f},{y:.1f}" for x, y, _ in pts)
        svg.append(f'<polyline fill="none" stroke="{COLORS[sched]}" stroke-width="2.4" points="{pstr}"/>')
        for x, y, _ in pts:
            svg.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="3.5" fill="{COLORS[sched]}" stroke="#ffffff" stroke-width="1"/>')

    # Legend
    lx, ly = ml + pw + 16, mt + 10
    for idx, sched in enumerate(ORDER):
        yy = ly + idx * 24
        svg.append(f'<line x1="{lx}" y1="{yy}" x2="{lx+20}" y2="{yy}" stroke="{COLORS[sched]}" stroke-width="3"/>')
        svg.append(f'<circle cx="{lx+10}" cy="{yy}" r="3.5" fill="{COLORS[sched]}"/>')
        svg.append(f'<text x="{lx+26}" y="{yy+4}" font-size="12" fill="#1f2937">{LABELS[sched]}</text>')

    svg.append("</svg>\n")
    with open(os.path.join(OUTDIR, out_name), "w", encoding="utf-8") as f:
        f.write("\n".join(svg))


def plot_improvement_minmin(imp_data, out_name="improvement_vs_minmin.svg"):
    sizes = [100, 200, 300, 500, 1000, 2000, 5000, 10000]
    algos = ["TABWO", "PSO", "GSA"]
    w, h = 840, 440
    ml, mr, mt, mb = 74, 120, 54, 62
    pw, ph = w - ml - mr, h - mt - mb

    vals = [imp_data[(a, n)] for a in algos for n in sizes if (a, n) in imp_data]
    ticks, ymin, ymax = nice_ticks(max(max(vals), 10.0), 6, min(vals))
    if ymin > 0:
        ymin = 0.0

    svg = [svg_header(w, h, "Perbaikan Makespan (%) terhadap Min-Min (Positif = Lebih Cepat)")]
    zero_y = mt + ph - (0.0 - ymin) / (ymax - ymin) * ph

    for tv in ticks:
        y = mt + ph - (tv - ymin) / (ymax - ymin) * ph
        stroke = "#374151" if abs(tv) < 1e-6 else "#e5e7eb"
        sw = "1.4" if abs(tv) < 1e-6 else "1"
        svg.append(f'<line x1="{ml}" y1="{y:.1f}" x2="{ml+pw}" y2="{y:.1f}" stroke="{stroke}" stroke-width="{sw}"/>')
        svg.append(f'<text x="{ml-8}" y="{y+4:.1f}" text-anchor="end" font-size="11" fill="#4b5563">{tv:.0f}%</text>')

    svg.append(f'<line x1="{ml}" y1="{mt}" x2="{ml}" y2="{mt+ph}" stroke="#9ca3af" stroke-width="1.2"/>')

    gw = pw / len(sizes)
    bw = gw * 0.24
    for i, n in enumerate(sizes):
        cx = ml + (i + 0.5) * gw
        svg.append(f'<text x="{cx:.1f}" y="{mt+ph+20}" text-anchor="middle" font-size="11" fill="#4b5563">{n:,}</text>')
        for j, a in enumerate(algos):
            val = imp_data[(a, n)]
            bx = cx + (j - 1) * (bw + 2) - bw / 2
            by = mt + ph - (val - ymin) / (ymax - ymin) * ph
            top = min(by, zero_y)
            bh = max(abs(by - zero_y), 1.0)
            svg.append(f'<rect x="{bx:.1f}" y="{top:.1f}" width="{bw:.1f}" height="{bh:.1f}" fill="{COLORS[a]}" rx="2"/>')
            if val > 0:
                svg.append(f'<text x="{bx+bw/2:.1f}" y="{top-4:.1f}" text-anchor="middle" font-size="9.5" font-weight="700" fill="{COLORS[a]}">+{val:.1f}%</text>')

    svg.append(f'<text x="{ml+pw/2:.1f}" y="{h-14}" text-anchor="middle" font-size="12" font-weight="600" fill="#374151">Jumlah Task (N)</text>')
    svg.append(f'<text x="18" y="{mt+ph/2:.1f}" text-anchor="middle" font-size="12" font-weight="600" fill="#374151" transform="rotate(-90 18 {mt+ph/2:.1f})">Perbaikan vs Min-Min (%)</text>')

    lx, ly = ml + pw + 16, mt + 14
    for idx, a in enumerate(algos):
        yy = ly + idx * 24
        svg.append(f'<rect x="{lx}" y="{yy-8}" width="14" height="14" fill="{COLORS[a]}" rx="2"/>')
        svg.append(f'<text x="{lx+22}" y="{yy+3}" font-size="12" fill="#1f2937">{LABELS[a]}</text>')

    svg.append("</svg>\n")
    with open(os.path.join(OUTDIR, out_name), "w", encoding="utf-8") as f:
        f.write("\n".join(svg))


def plot_energy_cpu_n10000(data, out_name="energy_cpu_n10000.svg"):
    w, h = 840, 380
    svg = [svg_header(w, h, "Konsumsi Energi & Utilisasi CPU pada N = 10.000")]

    panels = [
        ("energy_mean_Wh", "Energi (Wh) — lebih kecil lebih baik", 65, 390, 14000),
        ("cpu_util_mean_pct", "Utilisasi CPU (%) — lebih besar lebih baik", 465, 790, 100),
    ]
    mt, mb = 64, 58
    ph = h - mt - mb

    for key, subtitle, x0, x1, vmax in panels:
        pw = x1 - x0
        svg.append(f'<text x="{(x0+x1)/2:.1f}" y="52" text-anchor="middle" font-size="12.5" font-weight="600" fill="#374151">{subtitle}</text>')
        ticks, ymin, ymax = nice_ticks(vmax, 5, 0.0)
        for tv in ticks:
            y = mt + ph - (tv - ymin) / (ymax - ymin) * ph
            svg.append(f'<line x1="{x0}" y1="{y:.1f}" x2="{x1}" y2="{y:.1f}" stroke="#e5e7eb" stroke-width="1"/>')
            svg.append(f'<text x="{x0-6}" y="{y+4:.1f}" text-anchor="end" font-size="10.5" fill="#4b5563">{fmt_num(tv)}</text>')
        svg.append(f'<line x1="{x0}" y1="{mt}" x2="{x0}" y2="{mt+ph}" stroke="#9ca3af"/>')
        svg.append(f'<line x1="{x0}" y1="{mt+ph}" x2="{x1}" y2="{mt+ph}" stroke="#9ca3af"/>')

        bw = pw / len(ORDER) * 0.64
        step = pw / len(ORDER)
        for i, sched in enumerate(ORDER):
            val = data[(sched, 10000)][key]
            cx = x0 + (i + 0.5) * step
            bx = cx - bw / 2
            by = mt + ph - (val - ymin) / (ymax - ymin) * ph
            bh = (mt + ph) - by
            svg.append(f'<rect x="{bx:.1f}" y="{by:.1f}" width="{bw:.1f}" height="{bh:.1f}" fill="{COLORS[sched]}" rx="3"/>')
            lbl = f"{val:,.0f}" if key == "energy_mean_Wh" else f"{val:.1f}%"
            svg.append(f'<text x="{cx:.1f}" y="{by-5:.1f}" text-anchor="middle" font-size="10" font-weight="600" fill="#1f2937">{lbl}</text>')
            svg.append(f'<text x="{cx:.1f}" y="{mt+ph+18}" text-anchor="middle" font-size="10.5" fill="#374151">{LABELS[sched]}</text>')

    svg.append("</svg>\n")
    with open(os.path.join(OUTDIR, out_name), "w", encoding="utf-8") as f:
        f.write("\n".join(svg))


def plot_sched_time_n10000(data, out_name="sched_time_n10000.svg"):
    w, h = 760, 320
    ml, mr, mt, mb = 100, 90, 52, 46
    pw, ph = w - ml - mr, h - mt - mb

    items = sorted([(s, data[(s, 10000)]["sched_time_mean_ms"] / 1000.0) for s in ORDER], key=lambda x: x[1])
    vmax = max(v for _, v in items)
    ticks, ymin, ymax = nice_ticks(vmax, 5, 0.0)

    svg = [svg_header(w, h, "Rata-rata Waktu Komputasi Penjadwalan pada N = 10.000 (detik)")]
    for tv in ticks:
        x = ml + (tv - ymin) / (ymax - ymin) * pw
        svg.append(f'<line x1="{x:.1f}" y1="{mt}" x2="{x:.1f}" y2="{mt+ph}" stroke="#e5e7eb"/>')
        svg.append(f'<text x="{x:.1f}" y="{mt+ph+18}" text-anchor="middle" font-size="11" fill="#4b5563">{tv:.0f} s</text>')

    svg.append(f'<line x1="{ml}" y1="{mt}" x2="{ml}" y2="{mt+ph}" stroke="#9ca3af"/>')
    svg.append(f'<line x1="{ml}" y1="{mt+ph}" x2="{ml+pw}" y2="{mt+ph}" stroke="#9ca3af"/>')

    step = ph / len(items)
    bh = step * 0.58
    for i, (sched, val) in enumerate(items):
        cy = mt + (i + 0.5) * step
        by = cy - bh / 2
        bw = max((val - ymin) / (ymax - ymin) * pw, 2.0)
        svg.append(f'<text x="{ml-10}" y="{cy+4:.1f}" text-anchor="end" font-size="12" font-weight="600" fill="#1f2937">{LABELS[sched]}</text>')
        svg.append(f'<rect x="{ml}" y="{by:.1f}" width="{bw:.1f}" height="{bh:.1f}" fill="{COLORS[sched]}" rx="3"/>')
        lbl = f"{val*1000:.1f} ms" if val < 0.1 else f"{val:.2f} s"
        svg.append(f'<text x="{ml+bw+8:.1f}" y="{cy+4:.1f}" font-size="11.5" font-weight="600" fill="#1f2937">{lbl}</text>')

    svg.append("</svg>\n")
    with open(os.path.join(OUTDIR, out_name), "w", encoding="utf-8") as f:
        f.write("\n".join(svg))


def plot_convergence_n1000(out_name="convergence_n1000.svg"):
    seeds = [2000, 2017, 2034, 2051, 2068]
    algos = [("TABWO", "TABWO (TabwoFitness)"), ("PSO", "PSO (CommonFitness)"), ("GSA", "GSA (GsaFitness)")]
    w, h = 860, 320
    svg = [svg_header(w, h, "Kurva Konvergensi Rata-rata 5 Seed pada Wave 1, N = 1.000")]

    panel_coords = [(55, 280), (335, 560), (615, 840)]
    mt, mb = 62, 48
    ph = h - mt - mb

    for (algo, subtitle), (x0, x1) in zip(algos, panel_coords):
        pw = x1 - x0
        iter_vals = defaultdict(list)
        for sd in seeds:
            p = os.path.join("results", "convergence", f"{algo.lower()}_wave1_N1000_seed{sd}.csv")
            if not os.path.exists(p):
                continue
            with open(p, "r", encoding="utf-8") as f:
                for r in csv.DictReader(f):
                    iter_vals[int(r["iteration"])].append(float(r["best_fitness"]))
        iters = sorted(iter_vals.keys())
        means = [sum(iter_vals[it]) / len(iter_vals[it]) for it in iters]
        vmin, vmax = min(means), max(means)
        if abs(vmax - vmin) < 1e-6:
            vmin -= 0.05
            vmax += 0.05
        else:
            pad = (vmax - vmin) * 0.12
            vmin -= pad
            vmax += pad

        svg.append(f'<text x="{(x0+x1)/2:.1f}" y="50" text-anchor="middle" font-size="12" font-weight="600" fill="#374151">{subtitle}</text>')
        for k in range(5):
            tv = vmin + k * (vmax - vmin) / 4.0
            y = mt + ph - k / 4.0 * ph
            svg.append(f'<line x1="{x0}" y1="{y:.1f}" x2="{x1}" y2="{y:.1f}" stroke="#e5e7eb"/>')
            svg.append(f'<text x="{x0-5}" y="{y+4:.1f}" text-anchor="end" font-size="10" fill="#4b5563">{tv:.3f}</text>')

        svg.append(f'<line x1="{x0}" y1="{mt}" x2="{x0}" y2="{mt+ph}" stroke="#9ca3af"/>')
        svg.append(f'<line x1="{x0}" y1="{mt+ph}" x2="{x1}" y2="{mt+ph}" stroke="#9ca3af"/>')
        for it_lbl in (0, 50, iters[-1]):
            xx = x0 + (it_lbl / max(iters[-1], 1)) * pw
            svg.append(f'<text x="{xx:.1f}" y="{mt+ph+16}" text-anchor="middle" font-size="10" fill="#4b5563">{it_lbl}</text>')
        svg.append(f'<text x="{(x0+x1)/2:.1f}" y="{h-12}" text-anchor="middle" font-size="11" fill="#4b5563">Iterasi</text>')

        pts = []
        for it, val in zip(iters, means):
            xx = x0 + (it / max(iters[-1], 1)) * pw
            yy = mt + ph - (val - vmin) / (vmax - vmin) * ph
            pts.append(f"{xx:.1f},{yy:.1f}")
        svg.append(f'<polyline fill="none" stroke="{COLORS[algo]}" stroke-width="2.5" points="{" ".join(pts)}"/>')

        # Start -> End annotation
        svg.append(f'<text x="{x1-4}" y="{mt+14}" text-anchor="end" font-size="10.5" font-weight="600" fill="{COLORS[algo]}">{means[0]:.3f} &#8594; {means[-1]:.3f}</text>')

    svg.append("</svg>\n")
    with open(os.path.join(OUTDIR, out_name), "w", encoding="utf-8") as f:
        f.write("\n".join(svg))


def main():
    os.makedirs(OUTDIR, exist_ok=True)
    raw = load_raw_summary()
    imp = load_imp_minmin()

    plot_line_by_n(raw, "makespan_mean_s", "Perbandingan Makespan vs Jumlah Task (N)", "Makespan Rata-rata (detik)", "makespan_vs_tasks.svg")
    plot_improvement_minmin(imp, "improvement_vs_minmin.svg")
    plot_line_by_n(raw, "success_rate_mean_pct", "Perbandingan Success Rate vs Jumlah Task (N)", "Success Rate (%)", "success_rate_vs_tasks.svg")
    plot_energy_cpu_n10000(raw, "energy_cpu_n10000.svg")
    plot_sched_time_n10000(raw, "sched_time_n10000.svg")
    plot_convergence_n1000("convergence_n1000.svg")
    print("Generated 6 SVG charts in", OUTDIR)


if __name__ == "__main__":
    main()
