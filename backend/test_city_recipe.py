"""Test puri dell'estrattore di ricetta: nessun PBF, nessuna rete."""

import os
import sys
import unittest
from collections import Counter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "backend", "preprocessing"))

from city_recipe import bbox_area_m2, derive_recipe, parse_bbox  # noqa: E402


class FakeStats:
    def __init__(self):
        self.road_len = 0.0
        self.road_width_sum = 0.0
        self.road_count = 0
        self.road_classes = Counter()
        self.buildings = 0
        self.park_area = 0.0
        self.pois = Counter()


class TestCityRecipe(unittest.TestCase):
    BBOX = (41.44, 15.51, 41.49, 15.58)

    def test_bbox_e_area(self):
        self.assertEqual(parse_bbox("41.44,15.51,41.49,15.58"), self.BBOX)
        area = bbox_area_m2(self.BBOX)
        self.assertGreater(area, 10_000_000)
        self.assertLess(area, 100_000_000)

    def test_derived_grid_senumera_da_densita_stradale(self):
        s = FakeStats()
        s.road_len = 80_000.0  # m di strada nell'area
        s.road_width_sum = 600_000.0  # media 7,5 m
        s.road_count = 400
        s.road_classes["residential"] = 300
        s.buildings = 2500
        s.park_area = 1_500_000.0
        s.pois.update({"bar": 30, "school": 10, "dealer": 5, "repair": 5})

        r = derive_recipe(s, self.BBOX, ["italy-roads.pbf"])
        grid = r["grid"]
        self.assertEqual(r["version"], 1)
        self.assertGreaterEqual(grid["road_spacing_m"], 70.0)
        self.assertLessEqual(grid["road_spacing_m"], 400.0)
        self.assertAlmostEqual(grid["road_width_m"], 7.5, places=1)
        self.assertLess(grid["block_m"], grid["road_spacing_m"])
        self.assertGreaterEqual(grid["park_ratio"], 0.02)
        self.assertLessEqual(grid["park_ratio"], 0.25)
        self.assertAlmostEqual(sum(r["poi_mix"].values()), 1.0, places=3)
        self.assertIn("bar", r["poi_mix"])
        self.assertGreater(r["seed"], 0)

    def test_senza_dati_resta_entro_i_limiti(self):
        r = derive_recipe(FakeStats(), self.BBOX, ["x"])
        self.assertEqual(r["grid"]["road_spacing_m"], 160.0)
        self.assertEqual(r["grid"]["road_width_m"], 6.0)
        self.assertEqual(r["poi_mix"], {})
        self.assertEqual(r["raw"]["road_segments"], 0)


if __name__ == "__main__":
    unittest.main(verbosity=2)
