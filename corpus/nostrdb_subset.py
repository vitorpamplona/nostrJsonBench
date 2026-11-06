#!/usr/bin/env python3
"""
Builds a corpus directory for comparing a parser with nostrdb (Damus and Notedeck's parser).

nostrdb can't represent two kinds of corpus events: one with a \\uXXXX escape (its parser
rejects it) and one with an empty sig (its note has a fixed 64-byte sig, so it keeps whatever
its buffer held). The output directory has every workload of the source corpus, unchanged
(symlinked), and, for each workload that has such events, `<name>-ndb`: the same events in the
same order without them, with its own digest, so every parser can run on it.

    python3 corpus/nostrdb_subset.py [--source .corpus] --out DIR
    rust/run.sh --corpus DIR --workloads all-ndb,notes-ndb,...
"""
import argparse
import hashlib
import json
import os

from prepare import digest_event

HERE = os.path.dirname(os.path.abspath(__file__))


def unrepresentable(line):
    return b"\\u" in line or b'"sig":""' in line


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--source", default=os.path.join(os.path.dirname(HERE), ".corpus"))
    ap.add_argument("--out", required=True)
    a = ap.parse_args()
    src = os.path.abspath(a.source)
    os.makedirs(a.out, exist_ok=True)
    manifest = json.load(open(os.path.join(src, "manifest.json")))
    workloads = list(manifest["workloads"])
    for w in manifest["workloads"]:
        link = os.path.join(a.out, w["file"])
        if not os.path.lexists(link):
            os.symlink(os.path.join(src, w["file"]), link)
        with open(os.path.join(src, w["file"]), "rb") as f:
            lines = [l.rstrip(b"\n") for l in f]
        keep = [l for l in lines if not unrepresentable(l)]
        if len(keep) == len(lines):
            continue
        h = hashlib.sha256()
        tags = 0
        for line in keep:
            e = json.loads(line)
            digest_event(h, e)
            tags += len(e["tags"])
        name = w["name"] + "-ndb"
        with open(os.path.join(a.out, name + ".ndjson"), "wb") as f:
            for line in keep:
                f.write(line + b"\n")
        size = sum(len(l) for l in keep)
        workloads.append(
            dict(
                w,
                name=name,
                file=name + ".ndjson",
                description=w["description"]
                + " (without the events nostrdb cannot represent: a \\u escape, or an empty sig)",
                events=len(keep),
                bytes=size,
                avg_bytes=round(size / len(keep)),
                avg_tags=round(tags / len(keep), 1),
                digest=h.hexdigest(),
            )
        )
        print(f"{name}: {len(keep)} of {len(lines)} events")
    manifest["workloads"] = workloads
    with open(os.path.join(a.out, "manifest.json"), "w") as f:
        json.dump(manifest, f, indent=2)
        f.write("\n")


if __name__ == "__main__":
    main()
