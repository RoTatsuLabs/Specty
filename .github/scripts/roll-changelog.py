#!/usr/bin/env python3
"""Moves the [Unreleased] entries of an app changelog under the released version.

Leaves an empty [Unreleased] section on top and every older version untouched.
A changelog that already has the version section is left as it is.
"""
import argparse
import datetime
import re
import sys
from pathlib import Path

ENTRY = re.compile(r"^\s*[-*] \S")


def fail(message):
    print(f"::error::{message}")
    sys.exit(1)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--changelog", required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--date", default=datetime.date.today().isoformat())
    args = parser.parse_args()

    path = Path(args.changelog)
    if not path.is_file():
        fail(f"Changelog not found: {path}")
    lines = path.read_text(encoding="utf-8").splitlines()

    marker = f"## [{args.version}]"
    if any(line.strip() == marker or line.startswith(marker + " ") for line in lines):
        print(f"{path} already has {marker}; nothing to move.")
        return

    start = next((i for i, line in enumerate(lines) if line.strip() == "## [Unreleased]"), None)
    if start is None:
        fail(f"{path} has no [Unreleased] section.")
    end = next((i for i in range(start + 1, len(lines)) if lines[i].startswith("## ")), len(lines))
    if not any(ENTRY.match(line) for line in lines[start + 1:end]):
        fail(f"Nothing under [Unreleased] in {path}.")

    rolled = lines[:start + 1] + ["", f"{marker} - {args.date}"] + lines[start + 1:]
    path.write_text("\n".join(rolled) + "\n", encoding="utf-8")
    print(f"Moved [Unreleased] to {marker} - {args.date}.")


if __name__ == "__main__":
    main()
