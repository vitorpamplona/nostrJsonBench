#!/usr/bin/env python3
"""
Merges results/<harness>.json files into one Markdown report.

    python3 report/report.py                    # results/*.json -> results/report.md
    python3 report/report.py --results DIR --out FILE

Only parsers whose digest matched the corpus manifest are ranked; the rest are listed
under "Failed verification" with their error.
"""
import argparse
import glob
import json
import math
import os

HERE = os.path.dirname(os.path.abspath(__file__))
BENCH = os.path.dirname(HERE)

MODE_TITLES = {
    "typed": "Typed: JSON → the language's own Event with owned strings (what a client keeps)",
    "borrowed": "Borrowed: Event whose strings point into the input buffer",
    "dom": "DOM: the library's generic tree (not converted into an Event)",
    "baseline": "Baselines (not parsers)",
}
BASELINE_PARSER = ("jvm", "quartz-jackson")


def load(results_dir):
    runs = []
    for path in sorted(glob.glob(os.path.join(results_dir, "*.json"))):
        try:
            with open(path) as f:
                run = json.load(f)
        except (OSError, json.JSONDecodeError):
            continue
        if isinstance(run, dict) and "results" in run and "harness" in run:
            runs.append(run)
    return runs


def fmt(v, digits=0):
    if v is None:
        return "–"
    if isinstance(v, float) and math.isnan(v):
        return "–"
    if digits == 0:
        return f"{v:,.0f}"
    return f"{v:,.{digits}f}"


def gc_cell(r):
    if r.get("gc_count") is None:
        return "–"
    return f"{r['gc_count']} / {fmt(r.get('gc_ms'), 0)} ms"


def geomean(values):
    values = [v for v in values if v and v > 0]
    if not values:
        return None
    return math.exp(sum(math.log(v) for v in values) / len(values))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--results", default=os.path.join(BENCH, "results"))
    ap.add_argument("--manifest", default=os.path.join(BENCH, ".corpus", "manifest.json"))
    ap.add_argument("--out", default=None)
    args = ap.parse_args()
    out = args.out or os.path.join(args.results, "report.md")

    runs = load(args.results)
    manifest = None
    if os.path.exists(args.manifest):
        with open(args.manifest) as f:
            manifest = json.load(f)
    workloads = [w["name"] for w in manifest["workloads"]] if manifest else []

    rows = []
    for run in runs:
        for r in run["results"]:
            r = dict(r)
            r["harness"] = run["harness"]
            lang = run.get("language", run["harness"])
            # Node, Bun and Deno share one language; name the runtime so their rows differ.
            r["language"] = f"{lang} ({run['harness'].capitalize()})" if run["harness"] in ("node", "bun", "deno") else lang
            rows.append(r)
            if r["workload"] not in workloads:
                workloads.append(r["workload"])

    ok = [r for r in rows if r.get("mode") == "baseline" or (r.get("digest_ok") and r.get("median_ns"))]
    failed = [r for r in rows if r.get("mode") != "baseline" and not (r.get("digest_ok") and r.get("median_ns"))]

    base = {
        r["workload"]: r["median_ns"] / r["events"]
        for r in ok
        if (r["harness"], r["parser"]) == BASELINE_PARSER
    }

    L = []
    L.append("# nostrJsonBench report\n")
    if manifest:
        L.append(
            f"Corpus `{manifest['source']}` — {manifest['events']:,} unique events, "
            f"fingerprint `{manifest['fingerprint'][:16]}`"
            + (", re-serialized canonically" if manifest.get("canonical") else ", raw bytes as received")
            + ".\n"
        )
    L.append("| harness | runtime | host | settings |")
    L.append("|---|---|---|---|")
    for run in runs:
        host = run.get("host", {})
        s = run.get("settings", {})
        stale = ""
        if manifest and run.get("corpus_fingerprint") not in (None, manifest["fingerprint"]):
            stale = " ⚠ different corpus"
        L.append(
            f"| {run['harness']} | {run.get('runtime', '')}{stale} | {host.get('cpu', '')} ×{host.get('cores', '?')} "
            f"| warmup {s.get('warmup_ms')} ms, ≥{s.get('min_time_ms')} ms, ≥{s.get('min_rounds')} rounds |"
        )
    L.append("")
    L.append(
        "MB/s is input bytes per second of median round (higher is better). Single thread. "
        "`× quartz` is speed relative to Amethyst's production parser (`jvm/quartz-jackson`, "
        "Jackson + Quartz's EventDeserializer) on the same workload. Alloc is heap bytes and "
        "allocation count per parsed event; GC is collections / total GC time during measurement.\n"
    )

    if manifest:
        L.append("## Workloads\n")
        L.append("| workload | events | MB | avg bytes | avg tags | what |")
        L.append("|---|---:|---:|---:|---:|---|")
        for w in manifest["workloads"]:
            L.append(
                f"| {w['name']} | {w['events']:,} | {w['bytes'] / 1e6:.2f} | {w['avg_bytes']} | {w['avg_tags']} | {w['description']} |"
            )
        L.append("")

    headline = "all" if "all" in workloads else (workloads[0] if workloads else None)

    # One row per library and mode: the library's fastest verified way from the frame type to
    # an event, picked on the headline workload and kept for every workload. The library's
    # other rows, and rows a harness flags `variant` (breakdowns, a non-frame input), are
    # listed under "Variants" instead of competing in the rankings.
    def nspe_of(r):
        return r["median_ns"] / r["events"]

    groups = {}
    for r in ok:
        if r.get("mode") == "baseline":
            continue
        key = (r["harness"], r["language"], r.get("library") or r["parser"], r.get("mode"))
        groups.setdefault(key, {}).setdefault(r["parser"], {})[r["workload"]] = r
    primary = {}  # (harness, parser) -> group key
    for key, parsers in groups.items():
        candidates = [(p, per) for p, per in parsers.items() if not any(x.get("variant") for x in per.values())]
        if not candidates:
            continue
        best = min(candidates, key=lambda item: nspe_of(item[1][headline]) if headline in item[1] else float("inf"))
        primary[(key[0], best[0])] = key

    def is_primary(r):
        return r.get("mode") == "baseline" or (r["harness"], r["parser"]) in primary

    for mode in ("typed", "borrowed", "dom", "baseline"):
        mrows = [r for r in ok if r.get("mode") == mode and r["workload"] == headline and is_primary(r)]
        if not mrows:
            continue
        mrows.sort(key=lambda r: -(r.get("mb_per_s") or 0))
        L.append(f"## {MODE_TITLES[mode]} — `{headline}` workload\n")
        L.append("| # | language | parser | input | MB/s | ns/event | × quartz | alloc B/event | allocs/event | GC | retained B/event | license |")
        L.append("|---:|---|---|---|---:|---:|---:|---:|---:|---|---:|---|")
        for i, r in enumerate(mrows, 1):
            nspe = r["median_ns"] / r["events"]
            rel = base.get(r["workload"])
            L.append(
                f"| {i} | {r['language']} | `{r['parser']}` | {r.get('input', '')} | {fmt(r.get('mb_per_s'))} "
                f"| {fmt(nspe)} | {fmt(rel / nspe, 2) + '×' if rel else '–'} "
                f"| {fmt(r.get('alloc_bytes_per_event'))} | {fmt(r.get('allocs_per_event'), 1)} | {gc_cell(r)} "
                f"| {fmt(r.get('retained_bytes_per_event'))} | {r.get('license') or ''} |"
            )
        L.append("")

    # Matrix: every verified parser x workload, MB/s, plus geometric mean of × quartz.
    keyed = {}
    for r in ok:
        if r.get("mode") == "baseline" or not is_primary(r):
            continue
        keyed.setdefault((r["language"], r["parser"], r.get("mode")), {})[r["workload"]] = r
    if keyed:
        L.append("## Every workload (MB/s)\n")
        L.append(
            "Geomean × quartz is the geometric mean, over the workloads both ran, of the speed relative "
            "to `quartz-jackson` — one number per parser that doesn't let the big `all` workload dominate.\n"
        )
        L.append("| language | parser | mode | " + " | ".join(workloads) + " | geomean × quartz |")
        L.append("|---|---|---|" + "---:|" * len(workloads) + "---:|")

        def score(item):
            per = item[1]
            g = geomean([base[w] / (per[w]["median_ns"] / per[w]["events"]) for w in per if w in base])
            return g or 0

        for (lang, parser, mode), per in sorted(keyed.items(), key=lambda it: -score(it)):
            cells = [fmt(per[w].get("mb_per_s")) if w in per else "" for w in workloads]
            g = score(((lang, parser, mode), per))
            L.append(f"| {lang} | `{parser}` | {mode} | " + " | ".join(cells) + f" | {fmt(g, 2) + '×' if g else '–'} |")
        L.append("")

    # Variants: every other verified row of a library that has a headline row, on the headline
    # workload, next to that headline row.
    vrows = []
    for key, parsers in groups.items():
        lead = next((p for p in parsers if (key[0], p) in primary), None)
        lead_row = parsers[lead].get(headline) if lead else None
        for p, per in parsers.items():
            if p != lead and headline in per:
                vrows.append((key, p, per[headline], lead, lead_row))
    if vrows:
        L.append(f"## Variants — `{headline}` workload\n")
        L.append(
            "Other ways the same library was run (another entry point or input path) and rows a "
            "harness flags as variants (breakdowns, an input that isn't the frame type). Each "
            "library's headline row above is its fastest; × is this row's speed relative to it, "
            "– when the row has no headline row of its own.\n"
        )
        L.append("| language | library | mode | headline row | variant | input | ns/event | × headline |")
        L.append("|---|---|---|---|---|---|---:|---:|")
        vrows.sort(key=lambda v: (v[0][1], v[0][2], v[0][3], nspe_of(v[2])))
        for key, p, r, lead, lr in vrows:
            lead_cell = f"`{lead}`" if lead else "–"
            ratio = f"{fmt(nspe_of(lr) / nspe_of(r), 2)}×" if lr else "–"
            L.append(
                f"| {key[1]} | {key[2]} | {key[3]} | {lead_cell} | `{p}` | {r.get('input', '')} "
                f"| {fmt(nspe_of(r))} | {ratio} |"
            )
        L.append("")

    # The interning trade-off: where Quartz spends a parse, what interning costs and saves,
    # and what moving it from the parser to cache entry (Amethyst #4583) changed.
    trade = {}
    for r in ok:
        if r["harness"] == "jvm" and r["parser"] in ("quartz-jackson", "quartz-jackson-cacheshare", "quartz-loop", "quartz-loop-intern", "quartz-loop-plain"):
            trade.setdefault(r["workload"], {})[r["parser"]] = r
    if any("quartz-loop" in v and "quartz-loop-intern" in v for v in trade.values()):
        L.append("## Interning: parse cost vs. memory kept (JVM)\n")
        L.append(
            "`quartz-jackson` is the production parse (databind → `EventDeserializer` → `EventFactory`). "
            "`quartz-loop` is the same loop on a bare jackson-core parser and `quartz-loop-plain` also drops "
            "`EventFactory`. `quartz-loop-intern` adds the `String.intern()` on id, pubkey and every tag value "
            "that Quartz 1.19.0 and earlier did while parsing; `quartz-jackson-cacheshare` adds what LocalCache "
            "now does instead to an event it keeps (`Event.shareStrings`), for every event, i.e. the worst case "
            "where every event is new and kept. *Retained* is the heap still held after parsing the whole "
            "workload and keeping every event.\n"
        )
        L.append("| workload | quartz-jackson ns/ev | + cache share ns/ev | loop ns/ev | loop + intern ns/ev | plain ns/ev | retained B/ev (no sharing) | retained B/ev (interned) | interning saves |")
        L.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|")

        def nspe(r):
            return r["median_ns"] / r["events"] if r else None

        for w in workloads:
            t = trade.get(w)
            if not t or "quartz-loop" not in t or "quartz-loop-intern" not in t:
                continue
            a, b = t["quartz-loop-intern"], t["quartz-loop"]
            ra, rb = a.get("retained_bytes_per_event"), b.get("retained_bytes_per_event")
            saves = rb - ra if ra is not None and rb is not None else None
            L.append(
                f"| {w} | {fmt(nspe(t.get('quartz-jackson')))} | {fmt(nspe(t.get('quartz-jackson-cacheshare')))} "
                f"| {fmt(nspe(b))} | {fmt(nspe(a))} | {fmt(nspe(t.get('quartz-loop-plain')))} "
                f"| {fmt(rb)} | {fmt(ra)} | {fmt(100 * saves / rb, 1) + '%' if saves is not None and rb else '–'} |"
            )
        L.append("")

    if failed:
        L.append("## Failed verification (excluded from rankings)\n")
        L.append("| language | parser | workload | errors | error |")
        L.append("|---|---|---|---:|---|")
        for r in sorted(failed, key=lambda r: (r["language"], r["parser"], r["workload"])):
            err = (r.get("error") or ("digest mismatch" if not r.get("digest_ok") else "no timing")).replace("|", "\\|")
            L.append(f"| {r['language']} | `{r['parser']}` | {r['workload']} | {r.get('errors', '')} | {err[:160]} |")
        L.append("")

    libs = {}
    for r in rows:
        libs.setdefault((r["language"], r.get("library") or r["parser"]), (r.get("version"), r.get("license")))
    L.append("## Libraries\n")
    L.append("| language | library | version | license |")
    L.append("|---|---|---|---|")
    for (lang, lib), (ver, lic) in sorted(libs.items()):
        L.append(f"| {lang} | {lib} | {ver or ''} | {lic or ''} |")
    L.append("")

    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    with open(out, "w") as f:
        f.write("\n".join(L))
    print(f"wrote {out} ({len(ok)} verified rows, {len(failed)} failed)")


if __name__ == "__main__":
    main()
