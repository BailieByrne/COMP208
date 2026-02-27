import subprocess
from pathlib import Path

# Where this script lives
SCRIPT_DIR = Path(__file__).resolve().parent

# Project root (two folders up)
PROJECT_ROOT = SCRIPT_DIR.parent.parent

# Path to C++ executable
EXE = PROJECT_ROOT / "Backend" / "program"

# Output folder
OUT_ROOT = PROJECT_ROOT / "Sarinas-Testing" / "outputs"

# Number of Monte Carlo runs
RUNS = 5


def main():
    print("C++ program is at:", EXE)
    print("Saving outputs to:", OUT_ROOT)

    # Make sure outputs folder exists
    OUT_ROOT.mkdir(parents=True, exist_ok=True)

    for i in range(1, RUNS + 1):
        run_dir = OUT_ROOT / f"run_{i:04d}"
        run_dir.mkdir(parents=True, exist_ok=True)

        print(f"Running simulation {i}/{RUNS}...")

        # Run the program inside that folder
        subprocess.run([str(EXE)], cwd=str(run_dir), check=True)

        # Check file got created
        csv_file = run_dir / "predicted_prices.csv"
        if csv_file.exists():
            print("✅ Created:", csv_file)
        else:
            print("❌ Missing:", csv_file)

    print("All done!")


if __name__ == "__main__":
    main()
