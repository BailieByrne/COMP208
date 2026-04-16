import argparse
import csv
import math
import subprocess
import uuid
from datetime import datetime
from pathlib import Path


def read_prices(csv_path: Path):
    prices = []
    with csv_path.open("r", newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            prices.append(float(row["Price"]))
    return prices


def l2_metrics(actual, predicted):
    n = min(len(actual), len(predicted))
    if n == 0:
        return float("nan"), float("nan"), 0
    sq_sum = 0.0
    for i in range(n):
        diff = predicted[i] - actual[i]
        sq_sum += diff * diff
    l2_total = math.sqrt(sq_sum)
    l2_per_point = l2_total / n
    return l2_total, l2_per_point, n


def run_one(exe_path: Path, root: Path, ticker: str, difficulty: int, s0: float, seed: str):
    cmd = [
        str(exe_path),
        ticker,
        str(difficulty),
        f"{s0:.6f}",
        seed,
    ]
    completed = subprocess.run(cmd, cwd=str(root), capture_output=True, text=True)
    return completed.returncode, completed.stdout, completed.stderr


def main():
    parser = argparse.ArgumentParser(
        description="Run GBM+AI batches by difficulty and log L2 metrics with seeds."
    )
    parser.add_argument("--ticker", default="AAPL", help="Ticker to generate (default: AAPL)")
    parser.add_argument("--runs", type=int, default=100, help="Runs per difficulty (default: 100)")
    parser.add_argument("--s0", type=float, default=500.0, help="Initial price S0 (default: 500.0)")
    parser.add_argument(
        "--difficulties",
        nargs="+",
        type=int,
        default=[1, 2, 3, 4],
        help="Difficulty levels to test (default: 1 2 3 4)",
    )
    args = parser.parse_args()

    root = Path(__file__).resolve().parents[1]
    exe_path = root / "build" / "bin" / "Release" / "stock_sim.exe"

    if not exe_path.exists():
        raise FileNotFoundError(
            f"Executable not found: {exe_path}. Build first with: cmake --build build --config Release"
        )

    logs_dir = root / "L2Logs"
    logs_dir.mkdir(parents=True, exist_ok=True)

    ts = datetime.now().strftime("%Y%m%d-%H%M%S")
    runs_csv = logs_dir / f"l2_runs_{args.ticker}_{ts}.csv"
    summary_csv = logs_dir / f"l2_summary_{args.ticker}_{ts}.csv"
    errors_log = logs_dir / f"l2_errors_{args.ticker}_{ts}.log"

    stock_csv = root / f"{args.ticker}_stock_prices.csv"
    pred_csv = root / f"{args.ticker}_predicted_prices.csv"

    run_rows = []
    err_lines = []

    print(f"Running batch: ticker={args.ticker}, runs_per_difficulty={args.runs}, s0={args.s0}")
    print(f"Difficulties: {args.difficulties}")

    for difficulty in args.difficulties:
        if difficulty < 1 or difficulty > 4:
            print(f"Skipping invalid difficulty: {difficulty}")
            continue

        print(f"\nDifficulty {difficulty}")
        for run_idx in range(1, args.runs + 1):
            seed = str(uuid.uuid4())
            code, stdout, stderr = run_one(exe_path, root, args.ticker, difficulty, args.s0, seed)

            if code != 0:
                msg = (
                    f"[difficulty={difficulty} run={run_idx} seed={seed}] "
                    f"stock_sim failed (code={code}) stderr={stderr.strip()}"
                )
                print(msg)
                err_lines.append(msg)
                continue

            if not stock_csv.exists() or not pred_csv.exists():
                msg = (
                    f"[difficulty={difficulty} run={run_idx} seed={seed}] missing CSV output "
                    f"({stock_csv.name}, {pred_csv.name})"
                )
                print(msg)
                err_lines.append(msg)
                continue

            try:
                actual = read_prices(stock_csv)
                predicted = read_prices(pred_csv)
                l2_total, l2_per_point, n_points = l2_metrics(actual, predicted)
            except Exception as ex:
                msg = (
                    f"[difficulty={difficulty} run={run_idx} seed={seed}] "
                    f"failed to read/process csv: {ex}"
                )
                print(msg)
                err_lines.append(msg)
                continue

            run_rows.append(
                {
                    "timestamp": datetime.now().isoformat(timespec="seconds"),
                    "ticker": args.ticker,
                    "difficulty": difficulty,
                    "run_index": run_idx,
                    "seed": seed,
                    "points_used": n_points,
                    "l2_total": f"{l2_total:.10f}",
                    "l2_per_point": f"{l2_per_point:.10f}",
                }
            )

            print(
                f"  run {run_idx:03d}/{args.runs}: "
                f"seed={seed[:8]}... l2_total={l2_total:.4f} l2_per_point={l2_per_point:.6f}"
            )

    # Write per-run CSV
    with runs_csv.open("w", newline="", encoding="utf-8") as f:
        fieldnames = [
            "timestamp",
            "ticker",
            "difficulty",
            "run_index",
            "seed",
            "points_used",
            "l2_total",
            "l2_per_point",
        ]
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        for row in run_rows:
            writer.writerow(row)

    # Build and write summary CSV
    summary = {}
    for row in run_rows:
        d = int(row["difficulty"])
        summary.setdefault(d, {"count": 0, "sum_total": 0.0, "sum_per_point": 0.0})
        summary[d]["count"] += 1
        summary[d]["sum_total"] += float(row["l2_total"])
        summary[d]["sum_per_point"] += float(row["l2_per_point"])

    with summary_csv.open("w", newline="", encoding="utf-8") as f:
        fieldnames = [
            "difficulty",
            "completed_runs",
            "avg_l2_total",
            "avg_l2_per_point",
        ]
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        for d in sorted(summary.keys()):
            count = summary[d]["count"]
            avg_total = summary[d]["sum_total"] / count if count else float("nan")
            avg_per_point = summary[d]["sum_per_point"] / count if count else float("nan")
            writer.writerow(
                {
                    "difficulty": d,
                    "completed_runs": count,
                    "avg_l2_total": f"{avg_total:.10f}",
                    "avg_l2_per_point": f"{avg_per_point:.10f}",
                }
            )

    if err_lines:
        errors_log.write_text("\n".join(err_lines), encoding="utf-8")

    print("\nDone.")
    print(f"Per-run results: {runs_csv}")
    print(f"Summary results: {summary_csv}")
    if err_lines:
        print(f"Errors log: {errors_log}")


if __name__ == "__main__":
    main()
