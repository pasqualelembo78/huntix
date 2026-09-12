#!/usr/bin/env python3
"""Worker parallelo per gen-tile con progresso in tempo reale.
Legge le chiavi tile da stdin (una per riga) e lancia gen-tile
su N worker (env HUNTIX_PAR, default 4). Stampa su stderr una
riga di progresso a ogni tile completata.

Uso da shell:
  cat /tmp/todo.txt | ./venv/bin/python tile_worker.py [--skip-graph] [--no-index]

Env vars:
  HUNTIX_PAR         worker paralleli (default 4)
  HUNTIX_COUNTRY     paese (default italy)
  HUNTIX_LOG_FILE    file di log opzionale (appende)
"""
from __future__ import annotations
import datetime as _dt
import os
import subprocess
import sys
import time
from concurrent.futures import ThreadPoolExecutor, as_completed


def _now() -> str:
    return _dt.datetime.now().strftime("%H:%M:%S")


def run_one(key: str, skip_graph: bool, no_index: bool,
            country: str) -> tuple[str, bool, str]:
    cmd = [sys.executable, "osm_italy_processor.py", "gen-tile", key]
    if skip_graph:
        cmd.append("--skip-graph")
    if no_index:
        cmd.append("--no-index")
    env = os.environ.copy()
    env["HUNTIX_COUNTRY"] = country
    try:
        r = subprocess.run(cmd, capture_output=True, text=True,
                           timeout=600, env=env)
        return key, r.returncode == 0, r.stdout.strip().split("\n")[-1] if r.stdout.strip() else r.stderr.strip().split("\n")[-1] if r.stderr.strip() else ""
    except subprocess.TimeoutExpired:
        return key, False, f"timeout 600s"
    except Exception as e:
        return key, False, str(e)


def main() -> None:
    skip_graph = "--skip-graph" in sys.argv
    no_index = "--no-index" in sys.argv
    workers = int(os.environ.get("HUNTIX_PAR", "4"))
    country = os.environ.get("HUNTIX_COUNTRY", "italy")
    log_file = os.environ.get("HUNTIX_LOG_FILE")

    keys = [ln.strip() for ln in sys.stdin if ln.strip()]
    total = len(keys)
    if total == 0:
        print(f"[{_now()}] nessuna tile da generare", file=sys.stderr)
        return

    print(f"[{_now()}] avvio {total} tile su {workers} worker "
          f"(country={country} skip_graph={skip_graph})", file=sys.stderr)

    done = 0
    errors = 0
    t_start = time.monotonic()
    log_fh = open(log_file, "a") if log_file else None

    try:
        with ThreadPoolExecutor(max_workers=workers) as pool:
            futures = {pool.submit(run_one, k, skip_graph, no_index, country): k
                       for k in keys}
            for f in as_completed(futures):
                key, ok, detail = f.result()
                done += 1
                if not ok:
                    errors += 1
                elapsed = time.monotonic() - t_start
                rate = done / elapsed * 60 if elapsed > 0 else 0
                remaining = (total - done) / rate if rate > 0 else 0
                eta_m, eta_s = divmod(int(remaining), 60)
                pct = done / total * 100
                status = "OK" if ok else "ERR"
                line = (f"[{_now()}] [{done}/{total}] {pct:5.1f}% "
                        f"| {rate:.1f} tile/min "
                        f"| ETA: {eta_m}m{eta_s:02d}s "
                        f"| {key} {status}")
                if detail and not ok:
                    line += f" ({detail[:80]})"
                print(line, file=sys.stderr, flush=True)
                if log_fh:
                    log_fh.write(line + "\n")
                    log_fh.flush()
    finally:
        if log_fh:
            log_fh.close()

    elapsed = time.monotonic() - t_start
    m, s = divmod(int(elapsed), 60)
    print(f"[{_now()}] completato: {done}/{total} "
          f"({errors} errori) in {m}m{s:02d}s",
          file=sys.stderr, flush=True)
    if errors > 0:
        sys.exit(1)


if __name__ == "__main__":
    main()
