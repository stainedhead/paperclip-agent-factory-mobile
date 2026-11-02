#!/usr/bin/env python3 -I
"""Checks that every backticked identifier in the Decision paragraph of the listed ADRs exists in shared/ (review F-D-6).

    scripts/check-adr-identifiers.py [ADR numbers...]     default: 18 22 23 24

A sentence that starts with "Originally proposed as" is history and is skipped. Spans containing '=' are server query
parameters (wire format, not symbols) and are skipped; so are spans with a space (prose). Exits 1 and lists the
missing identifiers when any is not found as a whole word in a Kotlin file under shared/ (outside build directories).
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
ADR_FILE = ROOT / "docs" / "architectural-decision-record.md"
DEFAULT_ADRS = (18, 22, 23, 24)
HISTORY = re.compile(r"Originally proposed as.*?(?:\n|$)", re.S)


def kotlin_words() -> set[str]:
    words: set[str] = set()
    for path in (ROOT / "shared").rglob("*.kt"):
        if "build" in path.parts:
            continue
        words.update(re.findall(r"[A-Za-z_][A-Za-z0-9_]*", path.read_text(encoding="utf-8")))
    return words


def decision_paragraph(text: str, number: int) -> str:
    match = re.search(rf"^## ADR-{number:03d}:.*?(?=^## ADR-|\Z)", text, re.S | re.M)
    if not match:
        raise SystemExit(f"ADR-{number:03d} not found in {ADR_FILE}")
    paragraph = re.search(r"\*\*Decision\.\*\*(.*?)(?=\n\*\*|\Z)", match.group(0), re.S)
    if not paragraph:
        raise SystemExit(f"ADR-{number:03d} has no Decision paragraph")
    return HISTORY.sub("", paragraph.group(1))


def identifiers(paragraph: str) -> set[str]:
    found: set[str] = set()
    for span in re.findall(r"`([^`]+)`", paragraph):
        if "=" in span or " " in span:
            continue
        found.update(re.findall(r"[A-Za-z_][A-Za-z0-9_]*", span))
    return found


def main(argv: list[str]) -> int:
    numbers = [int(a) for a in argv] or list(DEFAULT_ADRS)
    text = ADR_FILE.read_text(encoding="utf-8")
    words = kotlin_words()
    failed = False
    for number in numbers:
        missing = sorted(i for i in identifiers(decision_paragraph(text, number)) if i not in words)
        if missing:
            failed = True
            print(f"ADR-{number:03d}: not found in shared/: {', '.join(missing)}")
        else:
            print(f"ADR-{number:03d}: all identifiers resolve")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
