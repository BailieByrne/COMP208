import sqlite3
import sys
from pathlib import Path

"""
AI GENERATED TO READ MAIN.DB
This script connects to the main.db SQLite database, retrieves all user tables, and prints their contents in a readable format. It handles cases where the database file is missing or when there are no user tables. To run the script, simply execute it with Python, optionally providing the path to the main.db file as an argument. If no argument is given, it defaults to looking for main.db in the DB directory of the project root.
"""

def read_db(db_path: Path) -> None:
    if not db_path.exists():
        print(f"DB file not found: {db_path}")
        return

    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row

    try:
        tables = conn.execute(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name"
        ).fetchall()

        if not tables:
            print("No user tables found.")
            return

        print(f"Reading database: {db_path}")
        print("-" * 80)

        for t in tables:
            table_name = t["name"]
            print(f"TABLE: {table_name}")

            cols = conn.execute(f"PRAGMA table_info({table_name})").fetchall()
            col_names = [c["name"] for c in cols]
            print("COLUMNS:", ", ".join(col_names))

            rows = conn.execute(f"SELECT * FROM {table_name}").fetchall()
            print(f"ROWS: {len(rows)}")

            for idx, row in enumerate(rows, 1):
                as_dict = {k: row[k] for k in row.keys()}
                print(f"  {idx}. {as_dict}")

            print("-" * 80)
    finally:
        conn.close()


if __name__ == "__main__":
    script_dir = Path(__file__).resolve().parent
    project_root = script_dir.parent

    if len(sys.argv) > 1:
        candidate = Path(sys.argv[1])
        db_path = candidate if candidate.is_absolute() else (Path.cwd() / candidate)
    else:
        db_path = project_root / "DB" / "main.db"

    read_db(db_path.resolve())
