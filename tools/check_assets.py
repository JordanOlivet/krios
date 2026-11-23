# /// script
# requires-python = ">=3.11"
# dependencies = []
# ///
"""Cross-checks each Krios mod's registered ids against its resources.

    uv run tools/check_assets.py

For every block and item registered in a mod's registry/ModBlocks.java and
ModItems.java: blockstate, loot table, item model, English name, pickaxe
tag. For every resource file: valid JSON, EN and FR lang keys aligned, no
lang key of another mod, and no namespaced reference (model, texture,
recipe, item) pointing at a file or id that does not exist. Reads sources
only: no build, no game. Exit code 1 on any finding.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
MODS = {"krios_core": REPO / "mods" / "core", "krios_industry": REPO / "mods" / "industry"}
RES = {ns: root / "common" / "src" / "main" / "resources" for ns, root in MODS.items()}

# Ids a recipe, loot table or tag may name that are neither a block nor an
# item: recipe types and serializers, data components.
OTHER_IDS = {"grinding", "engram_copy", "koios_data", "engram"}

findings: list[str] = []


def finding(text: str) -> None:
    findings.append(text)
    print("  " + text)


def exists(ns: str, kind: str, path: str) -> bool:
    base = RES.get(ns)
    if base is None:
        return True
    if kind == "texture":
        return (base / "assets" / ns / "textures" / f"{path}.png").is_file()
    if kind == "model":
        return (base / "assets" / ns / "models" / f"{path}.json").is_file()
    if kind == "recipe":
        return (base / "data" / ns / "recipe" / f"{path}.json").is_file()
    return True


def main() -> int:
    registered: dict[str, set[str]] = {}
    for ns, root in MODS.items():
        package = ns.split("_", 1)[1]
        registry = root / "common" / "src" / "main" / "java" / "io" / "lakio" / "krios" / package / "registry"
        blocks = sorted(set(re.findall(r'registerWithItem\("([a-z0-9_]+)"',
                                       (registry / "ModBlocks.java").read_text(encoding="utf-8"))))
        items = sorted(set(re.findall(r'register\("([a-z0-9_]+)"',
                                      (registry / "ModItems.java").read_text(encoding="utf-8"))))
        registered[ns] = set(blocks) | set(items)
        assets = RES[ns] / "assets" / ns
        data = RES[ns] / "data" / ns
        en = json.loads((assets / "lang" / "en_us.json").read_text(encoding="utf-8"))
        fr = json.loads((assets / "lang" / "fr_fr.json").read_text(encoding="utf-8"))
        pickaxe = RES[ns] / "data" / "minecraft" / "tags" / "block" / "mineable" / "pickaxe.json"
        mined = {value.split(":")[1] for value in json.loads(pickaxe.read_text(encoding="utf-8"))["values"]}
        print(f"{ns}: {len(blocks)} blocks, {len(items)} items, {len(en)} EN and {len(fr)} FR lang keys")
        for key in sorted(set(en) ^ set(fr)):
            finding(f"LANG {ns}: '{key}' is in one of en_us/fr_fr only")
        for key in en:
            if "krios_" in key and ns not in key:
                finding(f"LANG {ns}: '{key}' belongs to another mod")
        for block in blocks:
            missing = []
            if not (assets / "blockstates" / f"{block}.json").is_file():
                missing.append("blockstate")
            if not (data / "loot_table" / "blocks" / f"{block}.json").is_file():
                missing.append("loot table")
            if f"block.{ns}.{block}" not in en:
                missing.append("name")
            if not (assets / "models" / "item" / f"{block}.json").is_file():
                missing.append("item model")
            if block not in mined:
                missing.append("pickaxe tag")
            if missing:
                finding(f"BLOCK {ns}:{block} lacks: {', '.join(missing)}")
        for item in items:
            missing = []
            if not (assets / "models" / "item" / f"{item}.json").is_file():
                missing.append("item model")
            if f"item.{ns}.{item}" not in en:
                missing.append("name")
            if missing:
                finding(f"ITEM {ns}:{item} lacks: {', '.join(missing)}")

    for ns, base in RES.items():
        for file in sorted(base.rglob("*.json")):
            rel = file.relative_to(base).as_posix()
            text = file.read_text(encoding="utf-8")
            try:
                json.loads(text)
            except ValueError as e:
                finding(f"JSON {ns}/{rel}: {e}")
                continue
            for ref_ns, ref in sorted(set(re.findall(r'"#?(krios_[a-z]+):([a-z0-9_/]+)"', text))):
                ok = True
                if rel.startswith("assets/") and "/models/" in rel:
                    ok = exists(ref_ns, "texture", ref) or exists(ref_ns, "model", ref)
                elif rel.startswith("assets/") and "/blockstates/" in rel:
                    ok = exists(ref_ns, "model", ref)
                elif "/advancement/" in rel:
                    # "recipes/<name>" is the advancement's own id; anything else names a recipe or an item.
                    ok = (ref.startswith("recipes/") or exists(ref_ns, "recipe", ref)
                          or ref in registered.get(ref_ns, ()))
                elif "/recipe/" in rel or "/loot_table/" in rel or "/tags/" in rel:
                    tag = RES[ref_ns] / "data" / ref_ns / "tags" / "item" / f"{ref}.json" if ref_ns in RES else None
                    ok = (ref in registered.get(ref_ns, ()) or ref in OTHER_IDS or ref.startswith("blocks/")
                          or (tag is not None and tag.is_file()))
                if not ok:
                    finding(f"DANGLING {ns}/{rel}: {ref_ns}:{ref} does not exist")

    print(f"{len(findings)} finding(s)")
    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main())
