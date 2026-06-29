#!/usr/bin/env python3
"""Update fxxkHilife version fields in one shot.

Usage:
  python3 scripts/bump_version.py 2.7.6 25 "Fix ANC jump and battery case level"

This script updates:
- app/build.gradle.kts
- app/src/main/res/values/strings.xml
- README.md
- README_EN.md
- docs/index.html
- VERSION_MANAGEMENT.md current version + history row
- DEVELOPMENT_LOG.md release entry
"""
from __future__ import annotations

import re
import sys
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def write(path: str, text: str) -> None:
    (ROOT / path).write_text(text, encoding="utf-8")


def replace_regex(text: str, pattern: str, repl: str) -> str:
    new = re.sub(pattern, repl, text)
    if new == text:
        print(f"[warn] pattern not changed: {pattern}")
    return new


def main() -> int:
    if len(sys.argv) < 4:
        print('Usage: python3 scripts/bump_version.py <versionName> <versionCode> <summary>')
        return 2

    version = sys.argv[1].removeprefix("v")
    tag = f"v{version}"
    code = sys.argv[2]
    summary = " ".join(sys.argv[3:]).strip()
    today = date.today().isoformat()

    # Gradle / strings
    s = read("app/build.gradle.kts")
    s = replace_regex(s, r"versionCode = \d+", f"versionCode = {code}")
    s = replace_regex(s, r'versionName = "[^"]+"', f'versionName = "{version}"')
    write("app/build.gradle.kts", s)

    s = read("app/src/main/res/values/strings.xml")
    s = replace_regex(s, r'<string name="version_name">[^<]+</string>', f'<string name="version_name">{version}</string>')
    write("app/src/main/res/values/strings.xml", s)

    # README / docs: replace old vX.Y.Z tokens with new tag where they describe current version.
    for path in ["README.md", "README_EN.md", "docs/index.html"]:
        p = ROOT / path
        if not p.exists():
            continue
        s = p.read_text(encoding="utf-8")
        s = re.sub(r"v\d+\.\d+\.\d+", tag, s)
        p.write_text(s, encoding="utf-8")

    # VERSION_MANAGEMENT.md
    path = "VERSION_MANAGEMENT.md"
    s = read(path)
    s = replace_regex(s, r"- \*\*v[\d.]+\*\* \(versionCode=\d+, \d{4}-\d{2}-\d{2}\)", f"- **{tag}** (versionCode={code}, {today})")
    s = replace_regex(s, r"versionCode=\d+, versionName=\"[\d.]+\"", f"versionCode={code}, versionName=\"{version}\"")
    s = replace_regex(s, r"version_name=[\d.]+", f"version_name={version}")
    s = replace_regex(s, r"\| `README\.md` \| v[\d.]+ \|", f"| `README.md` | {tag} |")
    s = replace_regex(s, r"\| `README_EN\.md` \| v[\d.]+ \|", f"| `README_EN.md` | {tag} |")
    s = replace_regex(s, r"\| `DEVELOPMENT_LOG\.md` \| v[\d.]+ \(末尾\) \|", f"| `DEVELOPMENT_LOG.md` | {tag} (末尾) |")
    row = f"| {tag} | {code} | {today} | {summary} |\n"
    history_has_tag = any(line.startswith(f"| {tag} |") for line in s.splitlines())
    if not history_has_tag:
        s = s.replace("|------|------|------|---------|\n", "|------|------|------|---------|\n" + row)
    write(path, s)

    # DEVELOPMENT_LOG.md
    path = "DEVELOPMENT_LOG.md"
    s = read(path)
    entry = f"""

## {tag} ({today})

### 发布
- {summary}
- versionCode: {code}
- versionName: {version}
- tag: {tag}
"""
    if f"## {tag} (" not in s:
        s = s.rstrip() + entry + "\n"
    write(path, s)

    print(f"Updated version to {tag} ({code})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
