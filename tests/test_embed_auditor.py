import unittest

from live_tv.embed_auditor import inspect_html


class EmbedAuditorTests(unittest.TestCase):
    def test_extracts_supported_embed_tags(self):
        html = """
        <html><body>
          <iframe src="https://player.example.test/embed/1"></iframe>
          <embed src="/legacy/player.html">
          <object data="https://cdn.example.test/player/object"></object>
          <video src="/media/preview.mp4"></video>
          <source src="/media/stream.m3u8" type="application/x-mpegURL">
        </body></html>
        """

        embeds = inspect_html(html, base_url="https://tv.example.test/category")
        values = {(item.tag, item.url, item.external) for item in embeds}

        self.assertIn(
            ("iframe", "https://player.example.test/embed/1", True), values
        )
        self.assertIn(
            ("embed", "https://tv.example.test/legacy/player.html", False), values
        )
        self.assertIn(
            ("object", "https://cdn.example.test/player/object", True), values
        )

        # Direct media and playlist tags are intentionally not reported.
        self.assertFalse(any(item.tag in {"video", "source"} for item in embeds))

    def test_deduplicates_identical_embeds(self):
        html = """
        <iframe src="https://player.example.test/embed/1"></iframe>
        <iframe src="https://player.example.test/embed/1"></iframe>
        """
        embeds = inspect_html(html)
        self.assertEqual(len(embeds), 1)


if __name__ == "__main__":
    unittest.main()
