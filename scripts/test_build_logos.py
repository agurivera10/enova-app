import unittest
from scripts.build_logos import country_for, generate, normalize


class LogoMappingTest(unittest.TestCase):
    def test_normalize_and_region(self):
        self.assertEqual(normalize("TyC Spórts"), "tycsports")
        self.assertEqual(country_for("ESPN", "espnmx"), "AR")
        self.assertEqual(country_for("ESPN", "espn_usa"), "US")
        self.assertEqual(country_for("ESPN 2", "espn2mx"), "AR")
        self.assertEqual(country_for("ESPN", "espn_mx"), "MX")

    def test_raster_matching(self):
        table = "TyC Sports\ttycsports\nESPN\tespn\nDesconocido\tnope\n"
        channels = [
            {"id": "TyCSports.ar", "name": "TyC Sports", "alt_names": [], "country": "AR", "closed": None},
            {"id": "ESPN.ar", "name": "ESPN", "alt_names": [], "country": "AR", "closed": None}
        ]
        logos = [
            {"channel": "TyCSports.ar", "format": "PNG", "url": "https://example.com/tyc.png", "in_use": True},
            {"channel": "ESPN.ar", "format": "SVG", "url": "https://example.com/espn.svg", "in_use": True}
        ]
        actual = generate(channels, logos, table)
        self.assertEqual(actual, {"tycsports": "https://example.com/tyc.png"})


if __name__ == "__main__":
    unittest.main()
