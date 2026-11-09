#!/usr/bin/env python3
"""Compile the AGSL shaders embedded in SearchGlass.java with Skia.

`javac` does not look at AGSL: shaders are strings that only get compiled on
the device, at draw time. This script extracts the two shader constants from
the Java source, compiles them with the same SkSL compiler family Android uses
(via `skia-python`), and checks that the uniforms the Java side sets actually
exist.

Usage:
    python -m pip install skia-python
    python tools/check_agsl.py

Exit code 0 = both shaders compile and every uniform is declared.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

try:
    import skia
except ImportError:  # pragma: no cover - dependency hint only
    sys.exit("skia-python is required: python -m pip install skia-python")

SOURCE = Path(__file__).resolve().parent.parent / "module" / "src" / "com" / \
    "abradant" / "iossettings" / "SearchGlass.java"

# Uniforms the Java side always sets; a missing one means the shader and the
# render code disagree even if the shader itself compiles.
REQUIRED = ["size", "offset", "cornerRadii", "refractionHeight",
            "refractionAmount", "depthEffect"]


def extract_constant(text: str, name: str) -> str:
    match = re.search(rf"{name}\s*=\s*(.*?);\s*\n", text, re.S)
    if not match:
        raise SystemExit(f"{name} not found in {SOURCE}")
    literals = re.findall(r'"((?:[^"\\]|\\.)*)"', match.group(1))
    raw = "".join(literals)
    return (raw.replace("\\n", "\n").replace("\\t", "\t")
            .replace('\\"', '"').replace("\\\\", "\\"))


def main() -> int:
    text = SOURCE.read_text(encoding="utf-8")
    failed = False
    for name in ("LIQUID_GLASS_AGSL", "LENS_ONLY_AGSL"):
        source = extract_constant(text, name)
        try:
            effect = skia.RuntimeEffect.MakeForShader(source)
        except Exception as error:  # noqa: BLE001 - report the SkSL message
            print(f"{name}: FAILED\n{error}")
            failed = True
            continue
        uniforms = [u.name for u in effect.uniforms()]
        children = [c.name for c in effect.children()]
        missing = [u for u in REQUIRED if u not in uniforms]
        print(f"{name}: OK")
        print(f"  uniforms: {uniforms}")
        print(f"  children: {children}")
        if missing:
            print(f"  missing required uniforms: {missing}")
            failed = True
        if children != ["content"]:
            print("  expected exactly one child shader named 'content'")
            failed = True
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
