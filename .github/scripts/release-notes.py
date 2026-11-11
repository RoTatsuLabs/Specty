#!/usr/bin/env python3
"""Builds version-specific release notes from an app changelog.

Uses the [<version>] section, or the [Unreleased] section when the version has
not been written down yet. Fails when that section is missing, empty, malformed,
or does not match the version of the APK that was built, so an unverified
release never goes out. A short summary may sit above the first category.
"""
import argparse
import datetime
import re
import sys
from pathlib import Path

CATEGORIES = ("Additions", "Changes", "Fixes", "Other")
HEADING = re.compile(r"^## \[([^\]]+)\](?: - (\d{4}-\d{2}-\d{2}))?\s*$")
ENTRY = re.compile(r"^\s*[-*] \S")


def fail(message):
    print(f"::error::{message}")
    sys.exit(1)


def split_sections(text):
    sections = {}
    current = None
    for line in text.splitlines():
        if line.startswith("## "):
            match = HEADING.match(line)
            if not match:
                fail(f"Unrecognised changelog heading: {line}")
            current = match.group(1)
            if current in sections:
                fail(f"Changelog has two sections for {current}.")
            sections[current] = {"date": match.group(2), "lines": []}
        elif current is not None:
            sections[current]["lines"].append(line)
    return sections


def check_body(version, lines):
    body = "\n".join(lines).strip("\n")
    category = None
    counts = {}
    for line in body.splitlines():
        if line.startswith("### "):
            category = line[4:].strip()
            if category not in CATEGORIES:
                fail(f"Section {version} has an unknown category: {category}")
            counts[category] = 0
        elif ENTRY.match(line):
            if category is None:
                fail(f"Section {version} has an entry outside a category.")
            counts[category] += 1
    if not counts:
        fail(f"Section {version} has no categories.")
    empty = [name for name, count in counts.items() if count == 0]
    if empty:
        fail(f"Section {version} has empty categories: {', '.join(empty)}")
    return body


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--changelog", required=True)
    parser.add_argument("--app", required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--apk-version", required=True)
    parser.add_argument("--repo", required=True)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--source-sha", required=True)
    parser.add_argument("--prev-tag", default="")
    parser.add_argument("--commit-count", default="")
    parser.add_argument("--notes-out", required=True)
    parser.add_argument("--changelog-out", required=True)
    args = parser.parse_args()

    path = Path(args.changelog)
    if not path.is_file():
        fail(f"Changelog not found: {path}")
    if args.apk_version != args.version:
        fail(f"APK versionName {args.apk_version} does not match release version {args.version}.")

    sections = split_sections(path.read_text(encoding="utf-8"))
    section = sections.get(args.version)
    if section is None:
        pending = sections.get("Unreleased")
        if pending is None or not any(ENTRY.match(line) for line in pending["lines"]):
            fail(f"{path} has no [{args.version}] section and nothing under [Unreleased].")
        print(f"No [{args.version}] section yet; using the [Unreleased] entries.")
        section = {"date": datetime.date.today().isoformat(), "lines": pending["lines"]}
    body = check_body(args.version, section["lines"])

    footer = [f"Source commit: `{args.source_sha[:12]}`"]
    if args.prev_tag:
        count = f" ({args.commit_count} commits)" if args.commit_count else ""
        footer.append(
            f"Full changes: https://github.com/{args.repo}/compare/{args.prev_tag}...{args.tag}{count}"
        )
    else:
        footer.append("First release.")

    Path(args.notes_out).write_text(body + "\n\n---\n\n" + "\n\n".join(footer) + "\n", encoding="utf-8")

    heading = f"# {args.app} {args.version}\n"
    if section["date"]:
        heading += f"\nReleased {section['date']}\n"
    Path(args.changelog_out).write_text(heading + "\n" + body + "\n", encoding="utf-8")
    print(f"Release notes written for {args.app} {args.version}.")


if __name__ == "__main__":
    main()
