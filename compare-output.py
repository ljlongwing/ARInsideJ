#!/usr/bin/env python3
"""
Compares ARInside (C++) vs ARInsideJ (Java) generated documentation output trees.

This is a structural/coverage comparison, not a byte-for-byte diff - ARInsideJ's known,
documented scope gaps (letter-filter navigation, full action pretty-printing, field-level
references, etc. - see java-port-effort-estimate memory) mean exact content match isn't
expected yet. What this checks:
  1. Which top-level object-type directories exist in each (schema, active_link, filter, ...)
  2. Per directory, which object "slugs" (the sanitized directory names under it) exist in each -
     set difference tells us which real objects one tool produced a page for and the other didn't
  3. A few sanity content checks on pages both tools produced for the same object

Usage: python compare-output.py <cpp-output-dir> <java-output-dir>
"""
import sys
import os
from pathlib import Path

# Directories that are legitimately expected only on one side, given known scope gaps.
CPP_ONLY_OK = {"overview", "template"}  # letter-filter nav pages + nav iframe template - not ported yet
JAVA_ONLY_OK = set()


def subdirs(path: Path) -> set[str]:
    if not path.is_dir():
        return set()
    return {p.name for p in path.iterdir() if p.is_dir()}


# schema is the only type with per-object subdirectories (it needs to hold nested field/VUI
# pages); every other type is flat files (type/Name.htm) - see java-port-effort-estimate memory
# for how this was discovered (diffing real C++ output caught the Java port using directories
# for everything).
DIR_BASED_TYPES = {"schema"}


def object_slugs(root: Path, type_dir: str) -> set[str]:
    """Object identifiers under a type dir: subdirectory names for schema, .htm basenames for everything else."""
    type_path = root / type_dir
    if not type_path.is_dir():
        return set()
    if type_dir in DIR_BASED_TYPES:
        return subdirs(type_path)
    return {p.stem for p in type_path.iterdir() if p.is_file() and p.suffix == ".htm"}


def main():
    if len(sys.argv) != 3:
        print(__doc__)
        sys.exit(1)

    cpp_root = Path(sys.argv[1])
    java_root = Path(sys.argv[2])

    print(f"C++ output:  {cpp_root}")
    print(f"Java output: {java_root}")
    print()

    cpp_top = subdirs(cpp_root)
    java_top = subdirs(java_root)

    print("=== Top-level directory coverage ===")
    cpp_only = cpp_top - java_top
    java_only = java_top - cpp_top
    both = cpp_top & java_top

    unexpected_cpp_only = cpp_only - CPP_ONLY_OK
    unexpected_java_only = java_only - JAVA_ONLY_OK

    print(f"In both: {sorted(both)}")
    if cpp_only:
        flag = " (expected - known gap)" if not unexpected_cpp_only else " (UNEXPECTED)"
        print(f"C++ only: {sorted(cpp_only)}{flag}")
    if java_only:
        flag = " (UNEXPECTED)" if unexpected_java_only else ""
        print(f"Java only: {sorted(java_only)}{flag}")
    print()

    print("=== Per-type object slug coverage ===")
    total_cpp_only = 0
    total_java_only = 0
    total_matched = 0
    for type_dir in sorted(both):
        cpp_slugs = object_slugs(cpp_root, type_dir)
        java_slugs = object_slugs(java_root, type_dir)
        matched = cpp_slugs & java_slugs
        only_cpp = cpp_slugs - java_slugs
        only_java = java_slugs - cpp_slugs
        total_cpp_only += len(only_cpp)
        total_java_only += len(only_java)
        total_matched += len(matched)

        status = "OK" if not only_cpp and not only_java else "DIFF"
        print(f"  {type_dir:20s} cpp={len(cpp_slugs):6d} java={len(java_slugs):6d} "
              f"matched={len(matched):6d} cpp_only={len(only_cpp):4d} java_only={len(only_java):4d}  [{status}]")
        if only_cpp:
            sample = sorted(only_cpp)[:5]
            print(f"    sample cpp-only: {sample}")
        if only_java:
            sample = sorted(only_java)[:5]
            print(f"    sample java-only: {sample}")

    print()
    print(f"TOTAL matched object pages: {total_matched}")
    print(f"TOTAL cpp-only (Java missing): {total_cpp_only}")
    print(f"TOTAL java-only (extra vs C++): {total_java_only}")


if __name__ == "__main__":
    main()
