from __future__ import annotations

import argparse
import json
import sys
from collections import deque
from dataclasses import asdict, dataclass
from html.parser import HTMLParser
from typing import Iterable
from urllib.error import HTTPError, URLError
from urllib.parse import urldefrag, urljoin, urlparse
from urllib.request import Request, urlopen

USER_AGENT = "Live-TV-Embed-Auditor/0.1 (+authorized-use-only)"
EMBED_ATTRS = {
    "iframe": "src",
    "embed": "src",
    "object": "data",
    "video": "src",
    "source": "src",
}


@dataclass(frozen=True)
class Embed:
    page_url: str
    tag: str
    url: str
    external: bool


class PageParser(HTMLParser):
    def __init__(self, base_url: str) -> None:
        super().__init__(convert_charrefs=True)
        self.base_url = base_url
        self.links: list[str] = []
        self.embeds: list[tuple[str, str]] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        attr_map = {k.lower(): v for k, v in attrs if v}
        tag = tag.lower()

        if tag == "a" and "href" in attr_map:
            self.links.append(normalize_url(urljoin(self.base_url, attr_map["href"])))

        embed_attr = EMBED_ATTRS.get(tag)
        if embed_attr and embed_attr in attr_map:
            self.embeds.append(
                (tag, normalize_url(urljoin(self.base_url, attr_map[embed_attr])))
            )


def normalize_url(url: str) -> str:
    return urldefrag(url.strip())[0]


def hostname(url: str) -> str:
    return (urlparse(url).hostname or "").lower().rstrip(".")


def domain_allowed(url: str, allowed_domains: Iterable[str]) -> bool:
    host = hostname(url)
    for raw in allowed_domains:
        allowed = raw.strip().lower().rstrip(".")
        if host == allowed or host.endswith("." + allowed):
            return True
    return False


def is_http_url(url: str) -> bool:
    return urlparse(url).scheme.lower() in {"http", "https"}


def fetch_html(url: str, timeout: float) -> tuple[str, str]:
    req = Request(
        url,
        headers={
            "User-Agent": USER_AGENT,
            "Accept": "text/html,application/xhtml+xml;q=0.9,*/*;q=0.1",
        },
    )
    with urlopen(req, timeout=timeout) as response:
        final_url = response.geturl()
        content_type = response.headers.get_content_type()
        if content_type not in {"text/html", "application/xhtml+xml"}:
            raise ValueError(f"not HTML ({content_type})")
        charset = response.headers.get_content_charset() or "utf-8"
        return final_url, response.read().decode(charset, errors="replace")


def crawl(
    start_url: str,
    allowed_domains: list[str],
    max_pages: int,
    timeout: float,
) -> tuple[list[Embed], list[dict[str, str]]]:
    if not is_http_url(start_url):
        raise ValueError("start URL must use http or https")
    if not domain_allowed(start_url, allowed_domains):
        raise ValueError("start URL host must match an --allow-domain value")

    queue: deque[str] = deque([normalize_url(start_url)])
    visited: set[str] = set()
    embeds: list[Embed] = []
    errors: list[dict[str, str]] = []

    while queue and len(visited) < max_pages:
        requested_url = queue.popleft()
        if requested_url in visited or not domain_allowed(requested_url, allowed_domains):
            continue
        visited.add(requested_url)

        try:
            final_url, html = fetch_html(requested_url, timeout)
        except (HTTPError, URLError, TimeoutError, ValueError, OSError) as exc:
            errors.append({"url": requested_url, "error": str(exc)})
            continue

        # Do not follow a redirect that leaves the explicit fetch allowlist.
        if not domain_allowed(final_url, allowed_domains):
            errors.append(
                {
                    "url": requested_url,
                    "error": f"redirected to non-allowlisted host: {hostname(final_url)}",
                }
            )
            continue

        parser = PageParser(final_url)
        parser.feed(html)
        source_host = hostname(final_url)

        for tag, embed_url in parser.embeds:
            if not is_http_url(embed_url):
                continue
            embeds.append(
                Embed(
                    page_url=final_url,
                    tag=tag,
                    url=embed_url,
                    external=hostname(embed_url) != source_host,
                )
            )

        for link in parser.links:
            if (
                is_http_url(link)
                and domain_allowed(link, allowed_domains)
                and link not in visited
            ):
                queue.append(link)

    unique = list(dict.fromkeys(embeds))
    return unique, errors


def render_text(embeds: list[Embed], errors: list[dict[str, str]]) -> str:
    lines: list[str] = []
    for item in embeds:
        scope = "external" if item.external else "same-host"
        lines.append(f"[{item.tag}/{scope}] {item.url}\n  from: {item.page_url}")

    if errors:
        if lines:
            lines.append("")
        lines.append("Fetch errors:")
        for err in errors:
            lines.append(f"- {err['url']}: {err['error']}")

    if not lines:
        return "No embed/player URLs found."
    return "\n".join(lines)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description=(
            "Crawl allowlisted pages and report embedded player/media URLs. "
            "Use only on sites you own or are authorized to inspect."
        )
    )
    parser.add_argument("url", help="Authorized page or index URL to crawl")
    parser.add_argument(
        "--allow-domain",
        action="append",
        required=True,
        dest="allowed_domains",
        metavar="DOMAIN",
        help="Domain the crawler is allowed to fetch; repeat as needed",
    )
    parser.add_argument("--max-pages", type=int, default=50)
    parser.add_argument("--timeout", type=float, default=10.0)
    parser.add_argument("--json", action="store_true", dest="as_json")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)

    if args.max_pages < 1:
        print("error: --max-pages must be at least 1", file=sys.stderr)
        return 2
    if args.timeout <= 0:
        print("error: --timeout must be greater than 0", file=sys.stderr)
        return 2

    try:
        embeds, errors = crawl(
            start_url=args.url,
            allowed_domains=args.allowed_domains,
            max_pages=args.max_pages,
            timeout=args.timeout,
        )
    except ValueError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2

    if args.as_json:
        print(
            json.dumps(
                {
                    "embeds": [asdict(item) for item in embeds],
                    "errors": errors,
                },
                indent=2,
                ensure_ascii=False,
            )
        )
    else:
        print(render_text(embeds, errors))

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
