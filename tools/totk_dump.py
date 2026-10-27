#!/usr/bin/env python3
"""Inspect a local TOTK 1.4.3 ExeFS without copying or publishing game data.

The default report needs only Python's standard library. --analyze additionally
uses the inherited NSO reader (lz4) to inventory imports and relocations; it does
not recompile or execute the game. Hash agreement is not runtime compatibility.
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import sys

ROOT = Path(__file__).resolve().parents[1]
PROFILE = ROOT / "profiles/totk-1.4.3.json"


def inspect_module(path, expected):
    row = {"name": path.name, "expected_sha256": expected}
    if not path.is_file():
        return row | {"status": "missing"}
    try:
        digest = hashlib.sha256()
        with path.open("rb") as f:
            head = f.read(256)
            digest.update(head)
            size = len(head)
            for block in iter(lambda: f.read(1024 * 1024), b""):
                size += len(block)
                digest.update(block)
        row.update(size=size, sha256=digest.hexdigest())
        row["status"] = "match" if row["sha256"] == expected else "mismatch"
        if path.name == "main.npdm":
            if len(head) < 128 or head[:4] != b"META":
                raise ValueError("Invalid or truncated NPDM header")
        else:
            if len(head) != 256 or head[:4] != b"NSO0":
                raise ValueError("Invalid or truncated NSO header")
            flags, = struct.unpack_from("<I", head, 12)
            segments, end = [], 0
            for i in range(3):
                file_offset, memory_offset, length = struct.unpack_from("<III", head, 0x10 + i * 16)
                stored = struct.unpack_from("<I", head, 0x60 + i * 4)[0] if flags & (1 << i) else length
                if length and (file_offset < 256 or file_offset + stored > size or memory_offset < end):
                    raise ValueError("NSO segment out of bounds or overlapping in memory")
                end = memory_offset + length
                segments.append([memory_offset, length, stored])
            row.update(build_id=head[0x40:0x60].hex(), flags=flags, segments=segments,
                       image_size=end + struct.unpack_from("<I", head, 0x3c)[0])
    except ValueError as e:
        row.update(status="mismatch", error=str(e))
    except OSError as e:
        row.update(status="read_error", error=type(e).__name__)
    return row


def analyze_modules(exefs):
    from collections import Counter
    sys.path.insert(0, str(ROOT / "vendor/las-runtime/tools"))
    from nso import Nso
    from imports import host_impls
    mods = {name: Nso(exefs / name) for name in ("main", "subsdk0", "sdk")}
    exports = {name: {s[0] for s in mod.exports()} for name, mod in mods.items()}
    host = host_impls()
    rows = {}
    for name, mod in mods.items():
        pending, resolution = [], Counter()
        for sym, *_ in mod.imports():
            owners = [other for other, symbols in exports.items() if other != name and sym in symbols]
            if sym in host:
                resolution["host_name_found_unverified_semantics"] += 1
            elif owners:
                resolution["guest_export_found"] += 1
            else:
                resolution["unresolved"] += 1
                pending.append(sym)
        rows[name] = {"needed": mod.needed, "image_size": mod.size,
                      "imports": dict(resolution), "unresolved": sorted(set(pending)),
                      "relocations_by_type": dict(Counter(r[1] for r in mod.relocs)),
                      "dynamic_tags": sorted(mod.dyn)}
    return {"modules": rows, "note": "Static name coverage only; no runtime or HLE compatibility is implied."}


def inspect_dump(folder, profile, analyze=False):
    exefs = folder / "exefs" if (folder / "exefs").is_dir() else folder
    rows = [inspect_module(exefs / name, sha) for name, sha in profile["exefs_sha256"].items()]
    matches = all(r["status"] == "match" for r in rows)
    report = {"schema_version": 1, "target": "TOTK 1.4.3", "title_id": profile["title_id"],
              "reference": profile["source"], "modules": rows, "exefs_matches_reference": matches,
              "romfs": "folder_seen_not_validated" if (folder / "romfs").is_dir() else "not_checked",
              "runtime_status": "not_ported"}
    if analyze:
        if not matches:
            report["analysis_error"] = "Full analysis requires all five modules to match the reference."
        else:
            try:
                report["analysis"] = analyze_modules(exefs)
            except ImportError:
                report["analysis_error"] = "Install tools/requirements-totk.txt for --analyze."
            except (ValueError, AssertionError, IndexError, KeyError, TypeError, struct.error, OSError) as e:
                report["analysis_error"] = "NSO analysis failed: " + type(e).__name__
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("folder", type=Path, help="Folder containing exefs, or exefs itself")
    parser.add_argument("--output", type=Path, help="Write metadata JSON outside your extracted game folders")
    parser.add_argument("--analyze", action="store_true", help="Inventory three modules' imports and relocations")
    args = parser.parse_args()
    if args.output:
        # Keep the input read-only even if the user accidentally selects an input file as output.
        try:
            args.output.resolve().relative_to(args.folder.resolve())
        except ValueError:
            pass
        else:
            parser.error("--output must be outside the input folder")
    report = inspect_dump(args.folder, json.loads(PROFILE.read_text()), args.analyze)
    payload = json.dumps(report, indent=2) + "\n"
    if args.output:
        args.output.write_text(payload, encoding="utf-8")
    else:
        print(payload, end="")
    return 0 if report["exefs_matches_reference"] and "analysis_error" not in report else 1


if __name__ == "__main__":
    sys.exit(main())
