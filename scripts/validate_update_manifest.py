#!/usr/bin/env python3
"""Validate the checked-in update manifest against the current app version."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from urllib.parse import urlparse


ALLOWED_HOSTS = {
    "ct-yx.github.io",
    "github.com",
    "api.github.com",
    "objects.githubusercontent.com",
    "release-assets.githubusercontent.com",
}
HEX_64 = re.compile(r"^[0-9a-fA-F]{64}$")


def app_version() -> tuple[str, int]:
    text = Path("app/build.gradle.kts").read_text(encoding="utf-8")
    name = re.search(r'versionName\s*=\s*"([^"]+)"', text)
    code = re.search(r"versionCode\s*=\s*(\d+)", text)
    if not name or not code:
        raise ValueError("app version is missing")
    return name.group(1), int(code.group(1))


def check_url(value: str, field: str, required: bool = True) -> None:
    if not value and not required:
        return
    parsed = urlparse(value)
    if parsed.scheme != "https" or parsed.hostname not in ALLOWED_HOSTS:
        raise ValueError(f"{field} must be an HTTPS URL on an allowed host")


def main() -> int:
    manifest_path = Path(sys.argv[1] if len(sys.argv) > 1 else "docs/update.json")
    data = json.loads(manifest_path.read_text(encoding="utf-8"))
    required = {
        "schemaVersion",
        "channel",
        "versionName",
        "versionCode",
        "releaseUrl",
        "apkUrl",
        "sha256",
        "publishedAt",
        "minSdk",
        "notesUrl",
    }
    missing = sorted(required - data.keys())
    if missing:
        raise ValueError(f"missing fields: {', '.join(missing)}")
    if data["schemaVersion"] != 1 or data["channel"] != "stable":
        raise ValueError("only schemaVersion=1 stable manifests are supported")
    current_name, current_code = app_version()
    if not isinstance(data["versionCode"], int) or data["versionCode"] <= 0:
        raise ValueError("versionCode must be a positive integer")
    if data["versionCode"] > current_code:
        raise ValueError("manifest cannot advertise a version newer than the checked-out app")
    check_url(data["releaseUrl"], "releaseUrl")
    check_url(data["apkUrl"], "apkUrl", required=False)
    check_url(data["notesUrl"], "notesUrl", required=False)
    if data["apkUrl"] and not HEX_64.fullmatch(data["sha256"]):
        raise ValueError("sha256 must contain exactly 64 hexadecimal characters")
    print(
        f"update manifest OK: {manifest_path} {data['versionName']}/{data['versionCode']} "
        f"(checked-out app {current_name}/{current_code})"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(f"update manifest invalid: {error}", file=sys.stderr)
        raise SystemExit(1)
