# /// script
# requires-python = ">=3.11"
# dependencies = []
# ///
"""The Modrinth pages of the Krios mods, from the repository (docs/listing).

    uv run docs/listing/modrinth.py <core|industry> show                  # the page as Modrinth has it now
    uv run docs/listing/modrinth.py <core|industry> push-body             # <mod>/description.md as the page's body
    uv run docs/listing/modrinth.py <core|industry> push-summary          # <mod>/summary.txt as the short description
    uv run docs/listing/modrinth.py <core|industry> push-categories       # the featured categories (CATEGORIES below)
    uv run docs/listing/modrinth.py <core|industry> push-icon <png>       # the project icon
    uv run docs/listing/modrinth.py <core|industry> add-image <png|jpg> <title> [--featured]   # a gallery image; prints its URL
    uv run docs/listing/modrinth.py <core|industry> push-links            # source and issues links to GitHub
    uv run docs/listing/modrinth.py <core|industry> push-license          # the license of the mod's gradle.properties

The token is read from `modrinth.token` in local.properties (gitignored;
never commit it). Push only a text the user has validated, then read it back
with `show`: Modrinth keeps the last pushed body. A project under review
answers 404 on the public API; this script reads it with the token.
CurseForge has no such API for descriptions: its pages are pasted by hand.
"""
import json
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
API = "https://api.modrinth.com/v2"
# The project ids, as in the repository variables of the release workflows.
PROJECTS = {"core": "3MuVLFk8", "industry": "Spsf6zt6"}
# The three featured categories of each page (docs/listing/README.md).
CATEGORIES = {"core": ["technology", "library", "worldgen"],
              "industry": ["technology", "game-mechanics", "mobs"]}
REPOSITORY = "https://github.com/JordanOlivet/krios"
USER_AGENT = "JordanOlivet/krios listing script"
SUMMARY_MAX = 256


def token() -> str:
    file = ROOT / "local.properties"
    if file.exists():
        for line in file.read_text(encoding="utf-8").splitlines():
            key, _, value = line.partition("=")
            if key.strip() == "modrinth.token" and value.strip():
                return value.strip()
    sys.exit("No modrinth.token in local.properties")


def call(method: str, path: str, body=None, content_type: str = "application/json"):
    data = body if isinstance(body, (bytes, type(None))) else json.dumps(body).encode("utf-8")
    request = urllib.request.Request(API + path, data=data, method=method, headers={
        "Authorization": token(), "User-Agent": USER_AGENT, "Content-Type": content_type})
    try:
        with urllib.request.urlopen(request) as response:
            text = response.read().decode("utf-8")
            return response.status, json.loads(text) if text else None
    except urllib.error.HTTPError as e:
        sys.exit(f"{method} {path}: HTTP {e.code} {e.read().decode('utf-8', 'replace')}")


def show(project: str) -> None:
    _, p = call("GET", f"/project/{project}")
    for key in ("slug", "title", "description", "status", "categories", "additional_categories", "client_side",
                "server_side", "source_url", "issues_url", "wiki_url", "discord_url", "license", "icon_url"):
        print(f"{key}: {p.get(key)}")
    for image in p.get("gallery", []):
        print(f"gallery: {image.get('title')} featured={image.get('featured')} {image.get('raw_url') or image.get('url')}")
    print("--- body ---")
    print(p.get("body"))


def push_body(mod: str, project: str) -> None:
    body = (HERE / mod / "description.md").read_text(encoding="utf-8")
    status, _ = call("PATCH", f"/project/{project}", {"body": body})
    print(f"body pushed: HTTP {status}, {len(body)} characters")


def push_summary(mod: str, project: str) -> None:
    summary = " ".join((HERE / mod / "summary.txt").read_text(encoding="utf-8").split())
    if len(summary) > SUMMARY_MAX:
        sys.exit(f"{mod}/summary.txt is {len(summary)} characters, Modrinth takes {SUMMARY_MAX}")
    status, _ = call("PATCH", f"/project/{project}", {"description": summary})
    print(f"summary pushed: HTTP {status}, {len(summary)} characters")


def push_categories(mod: str, project: str) -> None:
    body = {"categories": CATEGORIES[mod], "additional_categories": []}
    status, _ = call("PATCH", f"/project/{project}", body)
    print(f"categories pushed: HTTP {status}, {', '.join(CATEGORIES[mod])}")


def push_icon(project: str, png: str) -> None:
    status, _ = call("PATCH", f"/project/{project}/icon?ext=png", Path(png).read_bytes(), "image/png")
    print(f"icon pushed: HTTP {status}")


def push_links(project: str) -> None:
    status, _ = call("PATCH", f"/project/{project}", {"source_url": REPOSITORY, "issues_url": REPOSITORY + "/issues"})
    print(f"links pushed: HTTP {status}")


def push_license(mod: str, project: str) -> None:
    props = (ROOT / "mods" / mod / "gradle.properties").read_text(encoding="utf-8").splitlines()
    spdx = next(line.split("=", 1)[1].strip() for line in props if line.startswith("license="))
    status, _ = call("PATCH", f"/project/{project}", {"license_id": spdx})
    print(f"license pushed: HTTP {status}, {spdx}")


def add_image(project: str, image: str, title: str, featured: bool) -> None:
    ext = Path(image).suffix.lower().lstrip(".")
    mime = "image/jpeg" if ext in ("jpg", "jpeg") else f"image/{ext}"
    query = f"ext={ext}&featured={'true' if featured else 'false'}&title={urllib.parse.quote(title)}"
    status, _ = call("POST", f"/project/{project}/gallery?{query}", Path(image).read_bytes(), mime)
    _, p = call("GET", f"/project/{project}")
    urls = [g["url"] for g in p.get("gallery", []) if g.get("title") == title]
    print(f"gallery image added: HTTP {status}, {urls[-1] if urls else 'URL not found'}")


def main() -> None:
    for stream in (sys.stdout, sys.stderr):
        stream.reconfigure(encoding="utf-8", errors="replace")
    args = sys.argv[1:]
    if len(args) < 1 or args[0] not in PROJECTS:
        sys.exit(__doc__)
    mod, project = args[0], PROJECTS[args[0]]
    command = args[1] if len(args) > 1 else "show"
    if command == "show":
        show(project)
    elif command == "push-body":
        push_body(mod, project)
    elif command == "push-summary":
        push_summary(mod, project)
    elif command == "push-categories":
        push_categories(mod, project)
    elif command == "push-icon" and len(args) > 2:
        push_icon(project, args[2])
    elif command == "push-links":
        push_links(project)
    elif command == "push-license":
        push_license(mod, project)
    elif command == "add-image" and len(args) > 3:
        add_image(project, args[2], args[3], "--featured" in args[4:])
    else:
        sys.exit(__doc__)


if __name__ == "__main__":
    main()
