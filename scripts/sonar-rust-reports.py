#!/usr/bin/env python3
"""Convert cargo-llvm-cov LCOV and Clippy JSON into SonarCloud generic reports."""

from __future__ import annotations

import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def project_path(value: str, root: Path, backend: Path) -> str:
    path = Path(value)
    if not path.is_absolute():
        if (root / path).exists():
            path = root / path
        else:
            path = backend / path
    return path.resolve().relative_to(root.resolve()).as_posix()


def parse_lcov(path: Path, root: Path, backend: Path) -> dict[str, dict[int, int]]:
    files: dict[str, dict[int, int]] = {}
    current_file: str | None = None
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.startswith("SF:"):
            current_file = project_path(line[3:], root, backend)
            files.setdefault(current_file, {})
        elif line.startswith("DA:") and current_file:
            number, hits, *_ = line[3:].split(",")
            files[current_file][int(number)] = int(hits)
    return files


def write_coverage(path: Path, files: dict[str, dict[int, int]]) -> None:
    coverage = ET.Element("coverage", version="1")
    for file_path, lines in sorted(files.items()):
        file_element = ET.SubElement(coverage, "file", path=file_path)
        for number, hits in sorted(lines.items()):
            ET.SubElement(
                file_element,
                "lineToCover",
                lineNumber=str(number),
                covered=str(hits > 0).lower(),
            )
    ET.indent(coverage, space="  ")
    ET.ElementTree(coverage).write(path, encoding="utf-8", xml_declaration=True)


def clippy_findings(path: Path, root: Path, backend: Path) -> tuple[list[dict], list[dict]]:
    rules: dict[str, dict] = {}
    issues: list[dict] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        try:
            event = json.loads(line)
        except json.JSONDecodeError:
            continue
        if event.get("reason") != "compiler-message":
            continue
        message = event.get("message", {})
        code = message.get("code") or {}
        rule_id = code.get("code")
        if message.get("level") != "warning" or not rule_id or not rule_id.startswith("clippy::"):
            continue
        spans = message.get("spans", [])
        primary = next((span for span in spans if span.get("is_primary")), None)
        if not primary:
            continue
        rules.setdefault(
            rule_id,
            {
                "id": rule_id,
                "name": rule_id,
                "description": message.get("message", rule_id),
                "engineId": "Clippy",
                "cleanCodeAttribute": "CLEAR",
                "impacts": [{"softwareQuality": "MAINTAINABILITY", "severity": "MEDIUM"}],
            },
        )
        location = {
            "message": message.get("message", rule_id),
            "filePath": project_path(primary["file_name"], root, backend),
            "textRange": {
                "startLine": primary["line_start"],
                "endLine": primary.get("line_end", primary["line_start"]),
                "startColumn": primary.get("column_start", 1),
                "endColumn": primary.get("column_end", primary.get("column_start", 1)),
            },
        }
        issues.append({"ruleId": rule_id, "effortMinutes": 0, "primaryLocation": location})
    return sorted(rules.values(), key=lambda item: item["id"]), issues


def main() -> int:
    if len(sys.argv) != 4:
        print("usage: sonar-rust-reports.py <repo-root> <lcov-info> <clippy-jsonl>", file=sys.stderr)
        return 2
    root = Path(sys.argv[1]).resolve()
    backend = root / "services/backend"
    reports = backend / "target/sonar"
    reports.mkdir(parents=True, exist_ok=True)
    covered_files = parse_lcov(Path(sys.argv[2]), root, backend)
    write_coverage(reports / "rust-coverage.xml", covered_files)
    rules, issues = clippy_findings(Path(sys.argv[3]), root, backend)
    (reports / "rust-issues.json").write_text(
        json.dumps({"rules": rules, "issues": issues}, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"Imported {len(covered_files)} Rust coverage files and {len(issues)} Clippy findings.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
