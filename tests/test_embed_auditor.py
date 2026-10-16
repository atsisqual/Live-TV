import unittest

from live_tv.embed_auditor import PageParser, domain_allowed, normalize_url


class DomainAllowlistTests(unittest.TestCase):
    def test_exact_domain_is_allowed(self):
        self.assertTrue(domain_allowed("https://example.test/a", ["example.test"]))

    def test_subdomain_is_allowed(self):
        self.assertTrue(domain_allowed("https://player.example.test/a", ["example.test"]))

    def test_suffix_trick_is_not_allowed(self):
        self.assertFalse(domain_allowed("https://notexample.test/a", ["example.test"]))


class PageParserTests(unittest.TestCase):
    def test_extracts_links_and_embeds(self):
        html = """
        <html><body>
          <a href="/channel/one">Channel</a>
          <iframe src="https://player.example.test/embed/1"></iframe>
          <video src="/media/preview.mp4"></video>
          <source src="/media/stream.m3u8" type="application/x-mpegURL">
        </body></html>
        """
        parser = PageParser("https://tv.example.test/category")
        parser.feed(html)

        self.assertEqual(parser.links, ["https://tv.example.test/channel/one"])
        self.assertIn(
            ("iframe", "https://player.example.test/embed/1"), parser.embeds
        )
        self.assertIn(("video", "https://tv.example.test/media/preview.mp4"), parser.embeds)
        self.assertIn(("source", "https://tv.example.test/media/stream.m3u8"), parser.embeds)

    def test_removes_fragments(self):
        self.assertEqual(
            normalize_url("https://example.test/page#player"),
            "https://example.test/page",
        )


if __name__ == "__main__":
    unittest.main()
