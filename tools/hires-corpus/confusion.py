#!/usr/bin/env python3
"""Prints truth-by-verdict confusion tables from an Evaluate.kt CSV. Usage: confusion.py results.csv"""
import csv, sys
from collections import Counter, defaultdict

rows = list(csv.DictReader(open(sys.argv[1])))
for key, title in (("verdict", "truth x verdict"),):
    cols = sorted({r[key] for r in rows})
    table = defaultdict(Counter)
    for r in rows:
        table[r["truth"]][r[key]] += 1
    print(f"{title}\n{'':16}" + "".join(f"{c[:11]:>12}" for c in cols))
    for t in sorted(table):
        print(f"{t:16}" + "".join(f"{table[t][c] or '.':>12}" for c in cols))
