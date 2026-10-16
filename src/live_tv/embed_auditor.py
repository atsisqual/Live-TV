from __future__ import annotations

import argparse
import json
import sys
from dataclasses import asdict, dataclass
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import urljoin, urlparse

EMBED_ATTRS = {
    "iframe": "src",
    "embed": "src",
    "object": "data",
}


@dataclass(frozen=True)
class Embed:
    tag: str
    url: str
    external: bool


class EmbedParser(HTMLParser):
    def __init__(self, base_url: str | None = None) -> None:
        super().__init__(convert_charrefs=True)
        self.base_url = base_url
        self.embeds: list[Embed] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        tag = tag.lower()
        attr_name = EMBED_ATTRS.get(tag)
        if not attr_name:
            return

        attr_map = {k.lower(): v for k, v in attrs if v}
        raw = attr_map.get(attr_name)
        if not raw:
            return

        url = urljoin(self.base_url, raw) if self.base_url else raw
        parsed = urlparse(url)
        base_host = urlparse(self.base_url).hostname if self.base_url else None
        external = bool(base_host and parsed.hostname and parsed.hostname != base_host)
        self.embeds.append(Embed(tag=tag, url=url, external=external))


def inspect_html(html: str, base_url: str | None = None) -> list[Embed]:
    parser = EmbedParser(base_url=base_url)
    parser.feed(html)
    return list(dict.fromkeys(parser.embeds))


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Inspect a local HTML file and list iframe/embed/object player URLs."
    )
    parser.add_argument("html_file", type=Path, help="Saved HTML file to inspect")
    parser.add_argument(
        "--base-url",
        help="Optional original page URL used to resolve relative embed paths",
    )
    parser.add_argument("--json", action="store_true", dest="as_json")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)

    if not args.html_file.is_file():
        print(f"error: file not found: {args.html_file}", file=sys.stderr)
        return 2

    try:
        html = args.html_file.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        html = args.html_file.read_text(encoding="utf-8", errors="replace")

    embeds = inspect_html(html, base_url=args.base_url)

    if args.as_json:
        print(json.dumps([asdict(item) for item in embeds], indent=2, ensure_ascii=False))
    elif not embeds:
        print("No iframe/embed/object URLs found.")
    else:
        for item in embeds:
            scope = "external" if item.external else "same-host/unknown"
            print(f"[{item.tag}/{scope}] {item.url}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
