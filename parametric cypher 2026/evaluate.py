#!/usr/bin/env python3
"""
evaluate.py — Post-processing metrics for link prediction CSV outputs.

Usage:
    python evaluate.py <csv1> [<csv2> ...] [--threshold 0.2] [--topk 100]

Examples:
    # Evaluate a single run
    python evaluate.py junehashgnnauc.csv

    # Compare all variants of one algorithm
    python evaluate.py june_hashgnn_*.csv

    # Compare across all algorithms
    python evaluate.py june_hashgnn_*.csv june_node2vec_*.csv june_graphsage_*.csv

    # Override threshold and top-K
    python evaluate.py junehashgnnauc.csv --threshold 0.5 --topk 200

Output:
    - Per-file metric table printed to stdout
    - Summary CSV written to metrics_summary.csv
"""

import sys
import argparse
import math
from pathlib import Path

import pandas as pd
import numpy as np
from scipy.stats import spearmanr
from sklearn.metrics import precision_score, recall_score, f1_score


# ─────────────────────────────────────────────
# Column names as produced by your Cypher RETURN
# ─────────────────────────────────────────────
COL_PROB        = "probability"
COL_TRANSMIT    = "transmitsScore"
COL_AUCPR_TEST  = "auprTest"
COL_AUCPR_VAL   = "auprValidation"
COL_AUCPR_TRAIN = "auprOuterTrain"
COL_JACCARD_DR  = "jaccardSimilarity"
COL_JACCARD_MUT = "jacSimMut"
COL_PATIENT1    = "patient1"
COL_PATIENT2    = "patient2"

# Columns that must parse as plain floats or empty (never contain commas).
# These are the anchor columns used by the robust parser.
# ORDER MATTERS: must match left-to-right position in the Cypher RETURN.
NUMERIC_COLS = [
    "auprTest", "auprValidation", "auprOuterTrain",
    "avgDegree", "avgClustering",
    # patient1  ← string, no commas expected
    # n1.Isolation_Country ← MAY contain commas  <── problem column
    "resistance1", "mutation1",
    # patient2  ← string, no commas expected
    # n2.Isolation_Country ← MAY contain commas  <── problem column
    "resistance2", "mutation2",
    "probability", "jaccardSimilarity", "jacSimMut",
    "distance", "mutdistance", "transmitsScore",
]


# ─────────────────────────────────────────────────────────────────────────────
# Robust CSV loader
# ─────────────────────────────────────────────────────────────────────────────

def _is_numeric_or_empty(s: str) -> bool:
    """Return True if s is a float, int, or empty string (NULL)."""
    s = s.strip()
    if s == "":
        return True
    try:
        float(s)
        return True
    except ValueError:
        return False


def _find_header_line(path: Path) -> tuple[int, list[str]]:
    """
    Scan lines to find the real header row — the first line whose comma-split
    contains 'probability'.  Returns (line_index, column_names).
    """
    with open(path, "r", encoding="utf-8", errors="replace") as fh:
        for i, line in enumerate(fh):
            cols = [c.strip() for c in line.rstrip("\n").split(",")]
            if COL_PROB in cols:
                return i, cols
    raise ValueError(f"Could not find a header row containing '{COL_PROB}' in {path.name}")


def _is_integer(s: str) -> bool:
    """Return True if s is a plain integer (resistance/mutation counts)."""
    try:
        int(s.strip())
        return True
    except ValueError:
        return False


def _parse_row_anchored(raw_fields: list[str], header_cols: list[str]) -> dict | None:
    """
    cypher-shell --format plain does not quote string values, so country names
    like "Korea, South" or "Congo, Dem. Rep." produce extra comma-delimited tokens.

    The schema has exactly two string columns that can expand:
      n1.Isolation_Country  (position 6 in a clean row)
      n2.Isolation_Country  (position 10 in a clean row)

    All surrounding columns are either numeric or plain IDs with no commas.

    Parsing strategy — anchor on resistance1/mutation1 (the INTEGER pair that
    immediately follows country1 in the schema):
      left  : [0..patient1]  — fixed, 6 fields
      right : [resistance2..]— fixed, 8 fields from the right end
      middle: everything in between = country1_tokens + resistance1 + mutation1
                                      + patient2 + country2_tokens

    We scan middle left-to-right for the first consecutive integer pair: those are
    resistance1/mutation1.  Everything before them is country1 (joined with ", ");
    the token immediately after is patient2; everything after that is country2.
    Country names are never pure integers, so the first integer pair is unambiguous.

    Returns a dict mapping header_col -> value, or None if the row is unrecoverable.
    """
    n_expected = len(header_cols)
    n_actual   = len(raw_fields)

    if n_actual == n_expected:
        return dict(zip(header_cols, raw_fields))
    if n_actual < n_expected:
        return None

    try:
        p1_idx = header_cols.index(COL_PATIENT1)   # 5
        r2_idx = header_cols.index("resistance2")  # 11
    except ValueError:
        return None

    # Fixed right anchor: from resistance2 to the end
    right_count = n_expected - r2_idx              # 19 - 11 = 8
    left        = raw_fields[:p1_idx + 1]          # cols 0-5 incl patient1
    right       = raw_fields[n_actual - right_count:]
    middle      = raw_fields[p1_idx + 1 : n_actual - right_count]
    # middle = [country1_tokens..., resistance1, mutation1, patient2, country2_tokens...]

    # Find first consecutive integer pair → resistance1, mutation1
    split_pos = None
    for j in range(len(middle) - 1):
        if _is_integer(middle[j]) and _is_integer(middle[j + 1]):
            split_pos = j
            break

    if split_pos is None:
        return None

    country1_tokens = middle[:split_pos]
    res1            = middle[split_pos]
    mut1            = middle[split_pos + 1]
    rest            = middle[split_pos + 2:]   # [patient2, country2_tokens...]

    if not rest:
        return None

    patient2        = rest[0]
    country2_tokens = rest[1:]

    country1  = ", ".join(t.strip() for t in country1_tokens)
    country2  = ", ".join(t.strip() for t in country2_tokens)

    canonical = left + [country1, res1, mut1, patient2, country2] + right

    if len(canonical) != n_expected:
        return None

    return dict(zip(header_cols, canonical))


def load_csv_robust(path: Path) -> pd.DataFrame:
    """
    Load a cypher-shell CSV that may have:
      1. Preamble lines from withjson.sh echo/cat before the header.
      2. Unquoted string columns (Isolation_Country) containing commas.

    Returns a cleaned DataFrame with correct dtypes.
    """
    header_line_idx, header_cols = _find_header_line(path)

    if header_line_idx > 0:
        print(
            f"  [INFO] {path.name}: skipping {header_line_idx} preamble line(s) "
            f"from withjson.sh diagnostic output.",
            file=sys.stderr,
        )

    records = []
    skipped = 0
    with open(path, "r", encoding="utf-8", errors="replace") as fh:
        for i, line in enumerate(fh):
            if i <= header_line_idx:
                continue  # skip preamble + header itself
            line = line.rstrip("\n")
            if not line.strip():
                continue  # skip blank lines
            raw = line.split(",")
            row = _parse_row_anchored(raw, header_cols)
            if row is None:
                skipped += 1
                continue
            records.append(row)

    if skipped:
        print(
            f"  [WARN] {path.name}: {skipped} row(s) could not be parsed and were skipped.",
            file=sys.stderr,
        )

    if not records:
        raise ValueError(f"No parseable data rows found in {path.name}")

    df = pd.DataFrame(records, columns=header_cols)

    # Coerce numeric columns — non-parseable values become NaN
    for col in df.columns:
        try:
            df[col] = pd.to_numeric(df[col], errors="ignore")
        except Exception:
            pass
    # Force-coerce the columns we definitely need as float
    for col in [COL_PROB, COL_TRANSMIT, COL_AUCPR_TEST, COL_AUCPR_VAL,
                COL_AUCPR_TRAIN, COL_JACCARD_DR, COL_JACCARD_MUT]:
        if col in df.columns:
            df[col] = pd.to_numeric(df[col], errors="coerce")

    return df


# ─────────────────────────────────────────────────────────────────────────────
# Labels and metric helpers
# ─────────────────────────────────────────────────────────────────────────────

def derive_label(df: pd.DataFrame) -> pd.Series:
    """
    transmitsScore is non-null → real TRANSMITS edge exists → label 1.
    transmitsScore is null     → t.weight = 0 or no edge    → label 0.
    """
    return df[COL_TRANSMIT].notna().astype(int)


def precision_at_k(y_true: np.ndarray, y_score: np.ndarray, k: int) -> float:
    if k == 0:
        return float("nan")
    order = np.argsort(y_score)[::-1]
    return float(y_true[order[:k]].sum()) / k


def recall_at_k(y_true: np.ndarray, y_score: np.ndarray, k: int) -> float:
    total_pos = y_true.sum()
    if total_pos == 0:
        return float("nan")
    order = np.argsort(y_score)[::-1]
    return float(y_true[order[:k]].sum()) / total_pos


def average_precision_at_k(y_true: np.ndarray, y_score: np.ndarray, k: int) -> float:
    order = np.argsort(y_score)[::-1][:k]
    hits, sum_prec = 0, 0.0
    for i, idx in enumerate(order):
        if y_true[idx] == 1:
            hits += 1
            sum_prec += hits / (i + 1)
    return sum_prec / max(hits, 1)


def brier_score(y_true: np.ndarray, y_score: np.ndarray) -> float:
    return float(np.mean((y_score - y_true) ** 2))


# ─────────────────────────────────────────────────────────────────────────────
# Main evaluation
# ─────────────────────────────────────────────────────────────────────────────

def evaluate_file(path: Path, threshold: float, topk: int) -> dict:
    try:
        df = load_csv_robust(path)
    except Exception as e:
        print(f"  [ERROR] Could not read {path.name}: {e}", file=sys.stderr)
        return {}

    missing = [c for c in [COL_PROB, COL_TRANSMIT] if c not in df.columns]
    if missing:
        print(f"  [WARN] {path.name}: missing columns {missing}. Skipping.", file=sys.stderr)
        return {}
    if df.empty:
        print(f"  [WARN] {path.name}: empty after parsing. Skipping.", file=sys.stderr)
        return {}

    aucpr_test  = float(df[COL_AUCPR_TEST].iloc[0])  if COL_AUCPR_TEST  in df.columns else float("nan")
    aucpr_val   = float(df[COL_AUCPR_VAL].iloc[0])   if COL_AUCPR_VAL   in df.columns else float("nan")
    aucpr_train = float(df[COL_AUCPR_TRAIN].iloc[0]) if COL_AUCPR_TRAIN in df.columns else float("nan")

    y_score     = df[COL_PROB].to_numpy(dtype=float)
    y_true      = derive_label(df).to_numpy(dtype=int)
    n           = len(df)
    n_pos       = int(y_true.sum())
    effective_k = min(topk, n)
    y_pred      = (y_score >= threshold).astype(int)

    def safe(fn, *a, **kw):
        try:
            return float(fn(*a, **kw))
        except Exception:
            return float("nan")

    prec_thresh = safe(precision_score, y_true, y_pred, zero_division=0)
    rec_thresh  = safe(recall_score,    y_true, y_pred, zero_division=0)
    f1_thresh   = safe(f1_score,        y_true, y_pred, zero_division=0)

    p_at_k   = precision_at_k(y_true, y_score, effective_k)
    r_at_k   = recall_at_k(y_true, y_score, effective_k)
    map_at_k = average_precision_at_k(y_true, y_score, effective_k)
    brier    = brier_score(y_true, y_score)

    ts = df[COL_TRANSMIT].to_numpy(dtype=float)
    mask_ts = ~np.isnan(ts) & ~np.isnan(y_score)
    spearman_r, spearman_p = (spearmanr(y_score[mask_ts], ts[mask_ts])
                               if mask_ts.sum() >= 3 else (float("nan"), float("nan")))

    jacc_dr  = df[COL_JACCARD_DR].to_numpy(dtype=float)  if COL_JACCARD_DR  in df.columns else np.full(n, np.nan)
    jacc_mut = df[COL_JACCARD_MUT].to_numpy(dtype=float) if COL_JACCARD_MUT in df.columns else np.full(n, np.nan)

    mask_dr = ~np.isnan(jacc_dr) & ~np.isnan(y_score)
    spearman_jacc_dr = (spearmanr(y_score[mask_dr], jacc_dr[mask_dr])[0]
                        if mask_dr.sum() >= 3 else float("nan"))

    mask_mut = ~np.isnan(jacc_mut) & ~np.isnan(y_score)
    spearman_jacc_mut = (spearmanr(y_score[mask_mut], jacc_mut[mask_mut])[0]
                         if mask_mut.sum() >= 3 else float("nan"))

    return {
        "file":                           path.name,
        "n_pairs":                        n,
        "n_positives":                    n_pos,
        "pct_positive":                   round(100.0 * n_pos / n, 1) if n > 0 else float("nan"),
        "aucpr_test (GDS)":               round(aucpr_test,  4),
        "aucpr_val  (GDS)":               round(aucpr_val,   4),
        "aucpr_train(GDS)":               round(aucpr_train, 4),
        f"precision@thr={threshold}":     round(prec_thresh, 4),
        f"recall@thr={threshold}":        round(rec_thresh,  4),
        f"f1@thr={threshold}":            round(f1_thresh,   4),
        f"precision@{effective_k}":       round(p_at_k,      4),
        f"recall@{effective_k}":          round(r_at_k,      4),
        f"MAP@{effective_k}":             round(map_at_k,    4),
        "brier_score":                    round(brier,       4),
        "spearman(prob,transmitScore)":   round(spearman_r,        4),
        "spearman_p(prob,transmitScore)": round(spearman_p,        4),
        "spearman(prob,jaccardDR)":       round(spearman_jacc_dr,  4),
        "spearman(prob,jaccardMut)":      round(spearman_jacc_mut, 4),
    }


# ─────────────────────────────────────────────────────────────────────────────
# Output formatting
# ─────────────────────────────────────────────────────────────────────────────

def fmt(v) -> str:
    if isinstance(v, float):
        return "   n/a  " if math.isnan(v) else f"{v:8.4f}"
    return str(v)


def print_table(records: list[dict]) -> None:
    if not records:
        return
    keys  = list(records[0].keys())
    col_w = {k: max(len(k), max(len(fmt(r.get(k, ""))) for r in records)) for k in keys}
    print("  ".join(k.ljust(col_w[k]) for k in keys))
    print("  ".join("-" * col_w[k] for k in keys))
    for r in records:
        print("  ".join(fmt(r.get(k, "")).ljust(col_w[k]) for k in keys))


# ─────────────────────────────────────────────────────────────────────────────
# Entry point
# ─────────────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description="Compute evaluation metrics from link prediction CSV outputs."
    )
    parser.add_argument("csvfiles", nargs="+", help="One or more CSV files to evaluate")
    parser.add_argument("--threshold", type=float, default=0.2,
                        help="Probability threshold for binary classification (default: 0.2)")
    parser.add_argument("--topk", type=int, default=100,
                        help="K for Precision@K, Recall@K, MAP@K (default: 100)")
    args = parser.parse_args()

    records = []
    for csv_path_str in args.csvfiles:
        p = Path(csv_path_str)
        if not p.exists():
            print(f"[WARN] File not found: {csv_path_str}", file=sys.stderr)
            continue
        print(f"Evaluating {p.name} ...", file=sys.stderr)
        result = evaluate_file(p, threshold=args.threshold, topk=args.topk)
        if result:
            records.append(result)

    if not records:
        print("No valid files to evaluate.", file=sys.stderr)
        sys.exit(1)

    print("\n" + "═" * 80)
    print("METRIC SUMMARY")
    print("═" * 80 + "\n")
    print_table(records)

    out_path = Path("metrics_summary.csv")
    pd.DataFrame(records).to_csv(out_path, index=False)
    print(f"\n✓ Full summary written to: {out_path.resolve()}")

    print("""
INTERPRETATION NOTES
────────────────────
aucpr_test (GDS)          GDS AUCPR on the held-out test split. Primary ranking metric.
aucpr_val  (GDS)          Avg AUCPR across validation folds. Gap vs test → overfit signal.
precision@thr             Of pairs predicted positive at threshold, fraction are real edges.
recall@thr                Of real edges in CSV, fraction were predicted positive.
f1@thr                    Harmonic mean of precision and recall at the threshold.
precision@K               Of top-K probability pairs, fraction are real edges.
MAP@K                     Mean Average Precision — rewards ranking true edges higher.
brier_score               Calibration: mean squared error of (probability - label). Lower = better.
spearman(prob,transmit)   Correlation of predicted probability with 1/t.weight.
                          High → model tracks phylogenetic proximity.
spearman(prob,jaccardDR)  Correlation with drug-resistance profile overlap.
spearman(prob,jaccardMut) Correlation with mutation profile overlap.
""")


if __name__ == "__main__":
    main()