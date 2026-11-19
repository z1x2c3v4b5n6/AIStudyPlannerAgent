import tempfile
import unittest
from pathlib import Path

import numpy as np

from run import atomic_cache, fuse, metrics, semantic, validate_vectors


class RetrievalTest(unittest.TestCase):
    def test_vectors_reject_wrong_dimension_nan_and_zero(self):
        for values in [np.ones((2, 3)), np.zeros((1, 4)), np.full((1, 4), np.nan)]:
            with self.assertRaises(ValueError):
                validate_vectors(values, 1, 4)
        np.testing.assert_allclose(np.linalg.norm(validate_vectors(np.ones((1, 4)), 1, 4)), 1)

    def test_rrf_records_each_source_and_stable_ties(self):
        config = dict(rrfK=60, keywordWeight=1, semanticWeight=1)
        hits, reasons = fuse([("a", 9), ("b", 8)], [("c", .9), ("b", .8)], config)
        self.assertEqual(["b", "a", "c"], [item[0] for item in hits])
        self.assertEqual(2, len(reasons["b"]))
        self.assertAlmostEqual(2 / 62, hits[0][1])
        config["semanticWeight"] = 3
        self.assertEqual("c", fuse([("a", 9)], [("c", .9)], config)[0][0][0])

    def test_cosine_threshold_domain_and_order(self):
        units = [dict(id="a", domain="JAVA_BACKEND"), dict(id="b", domain="AI_APPLICATION")]
        vectors = np.array([[1., 0.], [0., 1.]])
        config = dict(semanticThreshold=.5, candidateLimit=20)
        self.assertEqual([("a", 1.)], semantic(units, vectors, vectors[0], None, config))
        self.assertEqual([], semantic(units, vectors, vectors[0], "AI_APPLICATION", config))

    def test_cache_atomic_roundtrip(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "cache.npz"
            atomic_cache(path, {"version": 1}, np.ones((1, 4)))
            atomic_cache(path, {"version": 2}, np.ones((2, 4)))
            with np.load(path, allow_pickle=False) as cached:
                self.assertEqual((2, 4), cached["vectors"].shape)
                self.assertIn('"version": 2', str(cached["metadata"]))

    def test_negative_queries_and_partial_coverage(self):
        rows = [dict(ids=["x", "a"], relevantIds=["a", "b"]),
                dict(ids=["x"], relevantIds=[])]
        scores = metrics(rows, 3)
        self.assertEqual(.5, scores["recall"])
        self.assertEqual(.5, scores["mrr"])
        self.assertEqual(1, scores["negativeReturnRate"])
        self.assertEqual(.5, scores["multiTopicCoverage"])


if __name__ == "__main__":
    unittest.main()
