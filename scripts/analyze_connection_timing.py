#!/usr/bin/env python3
"""Summarize ConnPhase timing lines from an exported diagnostic report.

Usage:
  python3 scripts/analyze_connection_timing.py diagnostic.txt

The app writes one monotonic elapsed value per attempt and phase. This tool intentionally
ignores duplicate phase callbacks and reports missing phases instead of inventing a duration.
"""

from __future__ import annotations

import argparse
import math
import re
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path


PHASE_RE = re.compile(
    r"\[ConnPhase\]\s+attempt=(?P<attempt>\S+)\s+"
    r"trigger=(?P<trigger>\S+)\s+phase=(?P<phase>\S+)\s+"
    r"elapsed=(?P<elapsed>\d+)ms"
)


@dataclass
class Attempt:
    attempt_id: str
    trigger: str
    phases: dict[str, int] = field(default_factory=dict)


def parse(path: Path) -> list[Attempt]:
    attempts: dict[str, Attempt] = {}
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        match = PHASE_RE.search(line)
        if not match:
            continue
        attempt_id = match.group("attempt")
        attempt = attempts.setdefault(
            attempt_id,
            Attempt(attempt_id=attempt_id, trigger=match.group("trigger")),
        )
        phase = match.group("phase")
        # A duplicate callback must not replace the first boundary.
        attempt.phases.setdefault(phase, int(match.group("elapsed")))
    return list(attempts.values())


def duration(phases: dict[str, int], start: str, end: str) -> int | None:
    if start not in phases or end not in phases:
        return None
    return max(0, phases[end] - phases[start])


def percentile(values: list[int], ratio: float) -> int:
    values = sorted(values)
    index = max(0, math.ceil(len(values) * ratio) - 1)
    return values[index]


def metric_rows(attempt: Attempt) -> dict[str, int | None]:
    return {
        "request_to_transport": duration(attempt.phases, "Requested", "TransportReady"),
        "transport": duration(attempt.phases, "TransportConnecting", "TransportReady"),
        "transport_to_core": duration(attempt.phases, "TransportReady", "CoreReady"),
        "core_to_ready": duration(attempt.phases, "CoreReady", "Ready"),
        "core_to_degraded": duration(attempt.phases, "CoreReady", "Degraded"),
    }


def print_summary(attempts: list[Attempt]) -> None:
    if not attempts:
        print("No ConnPhase entries found.")
        return

    print("attempt_id\ttrigger\trequest_to_transport\ttransport\ttransport_to_core\tcore_to_ready\tstatus")
    grouped: dict[str, list[tuple[Attempt, dict[str, int | None]]]] = defaultdict(list)
    for attempt in attempts:
        metrics = metric_rows(attempt)
        status = "Failed" if "Failed" in attempt.phases else (
            "Ready" if "Ready" in attempt.phases else (
                "Degraded" if "Degraded" in attempt.phases else "Incomplete"
            )
        )
        print(
            "\t".join(
                [
                    attempt.attempt_id,
                    attempt.trigger,
                    *(str(metrics[key]) if metrics[key] is not None else "-" for key in (
                        "request_to_transport",
                        "transport",
                        "transport_to_core",
                        "core_to_ready",
                    )),
                    status,
                ]
            )
        )
        grouped[attempt.trigger].append((attempt, metrics))

    print("\nsummary_by_trigger")
    for trigger, rows in sorted(grouped.items()):
        print(f"[{trigger}] attempts={len(rows)}")
        for key in ("request_to_transport", "transport", "transport_to_core", "core_to_ready"):
            values = [metrics[key] for _, metrics in rows if metrics[key] is not None]
            if not values:
                print(f"  {key}: n/a")
                continue
            print(
                f"  {key}: n={len(values)} p50={percentile(values, 0.50)}ms "
                f"p95={percentile(values, 0.95)}ms max={max(values)}ms"
            )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("report", type=Path)
    args = parser.parse_args()
    print_summary(parse(args.report))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
