"""Opt-in offline retrieval experiment. No application startup hooks or network inference."""
import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import tempfile
import time
import zipfile

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, ensure_ascii=False).encode()).hexdigest()


def knowledge_text(unit):
    fields = ["topic", "aliases", "keyPoints", "learningObjectives", "prerequisites", "tags"]
    return "\n".join(
        str(unit[field]) if isinstance(unit[field], str) else " / ".join(unit[field])
        for field in fields
    )


def validate_vectors(vectors, count, dimension):
    if vectors.shape != (count, dimension) or not np.isfinite(vectors).all():
        raise ValueError("Invalid embedding shape or non-finite values")
    norms = np.linalg.norm(vectors, axis=1)
    if np.any(norms < 1e-8):
        raise ValueError("Zero embedding")
    return (vectors / norms[:, None]).astype(np.float32)


def atomic_cache(path, metadata, vectors):
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary = tempfile.mkstemp(dir=path.parent, suffix=".npz")
    try:
        with os.fdopen(descriptor, "wb") as output:
            np.savez_compressed(output, metadata=json.dumps(metadata), vectors=vectors)
        os.replace(temporary, path)
    finally:
        Path(temporary).unlink(missing_ok=True)


class Encoder:
    def __init__(self, directory, config):
        import onnxruntime as ort
        from tokenizers import Tokenizer

        self.config = config
        self.tokenizer = Tokenizer.from_file(str(directory / "tokenizer.json"))
        self.tokenizer.enable_truncation(max_length=config["maxLength"])
        self.tokenizer.enable_padding(pad_id=0, pad_token="[PAD]")
        options = ort.SessionOptions()
        options.intra_op_num_threads = config["threads"]
        options.inter_op_num_threads = 1
        self.session = ort.InferenceSession(
            str(directory / "model.onnx"), options, providers=["CPUExecutionProvider"]
        )

    def encode(self, texts):
        batches = []
        for offset in range(0, len(texts), 16):
            encoded = self.tokenizer.encode_batch(texts[offset:offset + 16])
            inputs = {
                "input_ids": np.array([item.ids for item in encoded], dtype=np.int64),
                "attention_mask": np.array([item.attention_mask for item in encoded], dtype=np.int64),
                "token_type_ids": np.array([item.type_ids for item in encoded], dtype=np.int64),
            }
            inputs = {item.name: inputs[item.name] for item in self.session.get_inputs()}
            hidden = self.session.run(None, inputs)[0]
            mask = inputs["attention_mask"][:, :, None]
            pooled = (hidden * mask).sum(axis=1) / np.maximum(mask.sum(axis=1), 1)
            batches.append(validate_vectors(pooled, len(encoded), self.config["dimension"]))
        return np.concatenate(batches)


def semantic(units, vectors, query_vector, domain, config):
    similarities = vectors @ query_vector
    eligible = [
        (unit["id"], float(similarities[index]))
        for index, unit in enumerate(units)
        if (domain is None or unit["domain"] == domain)
        and similarities[index] >= config["semanticThreshold"]
    ]
    return sorted(eligible, key=lambda item: (-item[1], item[0]))[:config["candidateLimit"]]


def fuse(keyword, semantic_hits, config):
    evidence = {}
    for source, hits, weight in (
        ("keyword", keyword, config["keywordWeight"]),
        ("semantic", semantic_hits, config["semanticWeight"]),
    ):
        for rank, (identifier, _) in enumerate(hits, 1):
            contribution = weight / (config["rrfK"] + rank)
            evidence.setdefault(identifier, []).append({
                "source": source, "rank": rank, "contribution": contribution
            })
    scores = {identifier: sum(item["contribution"] for item in reasons)
              for identifier, reasons in evidence.items()}
    ranking = sorted(scores, key=lambda identifier: (-scores[identifier], identifier))
    return [(identifier, scores[identifier]) for identifier in ranking], evidence


def metrics(rows, k):
    positives = [row for row in rows if row["relevantIds"]]
    values = []
    for row in positives:
        relevant = set(row["relevantIds"])
        retrieved = row["ids"][:k]
        ranks = [i + 1 for i, identifier in enumerate(retrieved) if identifier in relevant]
        values.append([float(bool(ranks)), len(ranks) / len(retrieved) if retrieved else 0,
                       len(ranks) / len(relevant), 1 / min(ranks) if ranks else 0])
    averages = np.mean(values, axis=0).tolist() if values else [None] * 4
    negatives = [row for row in rows if not row["relevantIds"]]
    return dict(zip(["hit", "precision", "recall", "mrr"], averages)) | {
        "positiveCount": len(positives), "negativeCount": len(negatives),
        "negativeReturnRate": sum(bool(row["ids"][:k]) for row in negatives) / len(negatives)
        if negatives else None,
        "multiTopicCoverage": float(np.mean([
            len(set(row["ids"][:k]) & set(row["relevantIds"])) / len(row["relevantIds"])
            for row in positives if len(row["relevantIds"]) > 1
        ])) if any(len(row["relevantIds"]) > 1 for row in positives) else None,
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--model-dir", type=Path, default=HERE / "model")
    parser.add_argument("--cache", type=Path, default=ROOT / "backend/target/c5-vectors.npz")
    parser.add_argument("--output", type=Path, default=HERE / "results.json")
    parser.add_argument("--config", type=Path, default=HERE / "config.json")
    parser.add_argument("--split", choices=["dev", "evaluate"], default="evaluate")
    parser.add_argument("--mode", choices=["keyword", "semantic", "hybrid", "all"], default="all")
    args = parser.parse_args()
    config = json.loads(args.config.read_text())
    if (config["rrfK"] <= 0 or config["candidateLimit"] < 8
            or config["candidateLimit"] > 20 or config["threads"] < 1
            or not -1 <= config["semanticThreshold"] <= 1
            or not all(math.isfinite(config[key]) and config[key] > 0
                       for key in ["keywordWeight", "semanticWeight"])):
        raise ValueError("Invalid fusion or semantic settings")
    cases = json.loads((HERE / "cases.json").read_text())
    if args.split == "dev":
        cases = [case for case in cases if case["split"] == "dev"]
    else:
        cases = [case for case in cases if case["split"] == "final"]
        legacy = json.loads((ROOT / "backend/src/test/resources/evaluation/knowledge-retrieval-eval.json").read_text())
        cases = [dict(case, split="legacy", category="legacy") for case in legacy] + cases
    exported = [] if args.mode == "semantic" else json.loads(
        (ROOT / "backend/target/retrieval-keyword.json").read_text())
    keywords = {row["id"]: row for row in exported}
    units = []
    for path in sorted((ROOT / "backend/src/main/resources/knowledge").glob("*.json")):
        units.extend(json.loads(path.read_text())["units"])
    known = {unit["id"] for unit in units}
    for case in cases:
        if not set(case["relevantIds"]) <= known:
            raise ValueError("Unknown relevant ID")
        if args.mode != "semantic":
            row = keywords[case["id"]]
            if row["query"] != case["query"] or row["domain"] != case["domain"]:
                raise ValueError("Keyword export is stale")
    encoder = None
    index_seconds = load_seconds = 0
    cache_hit = False
    vectors = None
    if args.mode != "keyword":
        model_hashes = {name: hashlib.sha256((args.model_dir / name).read_bytes()).hexdigest()
                        for name in ["model.onnx", "tokenizer.json"]}
        expected = json.loads((HERE / "model-manifest.json").read_text())
        if model_hashes != expected:
            raise ValueError("Model artifact hashes do not match the pinned model manifest")
        metadata = {"model": config["model"], "revision": config["revision"],
                    "artifact": config["artifact"], "hashes": model_hashes,
                    "dimension": config["dimension"], "maxLength": config["maxLength"],
                    "textVersion": 1, "knowledge": digest(units)}
        start = time.perf_counter()
        encoder = Encoder(args.model_dir, config)
        load_seconds = time.perf_counter() - start
        start = time.perf_counter()
        try:
            with np.load(args.cache, allow_pickle=False) as cached:
                if json.loads(str(cached["metadata"])) == metadata:
                    vectors = validate_vectors(cached["vectors"], len(units), config["dimension"])
                    cache_hit = True
        except (OSError, ValueError, KeyError, EOFError, zipfile.BadZipFile):
            pass
        if vectors is None:
            vectors = encoder.encode([knowledge_text(unit) for unit in units])
            atomic_cache(args.cache, metadata, vectors)
        index_seconds = time.perf_counter() - start
    modes = ["keyword", "semantic", "hybrid"] if args.mode == "all" else [args.mode]
    rows = {mode: [] for mode in modes}
    timings = {mode: [] for mode in modes}
    for case in cases:
        keyword_row = keywords.get(case["id"], {"rankings": [], "latencyMs": []})
        keyword = [(item["unit"]["id"], item["score"]) for item in keyword_row["rankings"]]
        semantic_hits = []
        semantic_times = []
        if encoder is not None:
            encoder.encode([case["query"]])  # warmup, no query vector cache
            for repeat in range(5):
                start = time.perf_counter()
                query_vector = encoder.encode([case["query"]])[0]
                semantic_hits = semantic(units, vectors, query_vector, case["domain"], config)
                semantic_times.append((time.perf_counter() - start) * 1000)
        start = time.perf_counter()
        hybrid, evidence = fuse(keyword, semantic_hits, config)
        fusion_ms = (time.perf_counter() - start) * 1000
        for mode in modes:
            ranking = {"keyword": keyword, "semantic": semantic_hits, "hybrid": hybrid}[mode]
            row = dict(case, ids=[item[0] for item in ranking[:8]], scores=ranking[:8])
            if mode == "hybrid":
                row["evidence"] = {identifier: evidence[identifier] for identifier in row["ids"]}
            rows[mode].append(row)
            keyword_times = keyword_row["latencyMs"]
            timings[mode].extend(keyword_times if mode == "keyword" else semantic_times
                                 if mode == "semantic" else [
                                     value + keyword_times[index] + fusion_ms
                                     for index, value in enumerate(semantic_times)])
    summary = {}
    for mode in modes:
        summary[mode] = {
            scope: {str(k): metrics([row for row in rows[mode]
                                    if (row["split"] == scope if scope in ["legacy", "final", "dev"]
                                        else row["domain"] == scope)], k) for k in [3, 8]}
            for scope in (["dev"] if args.split == "dev" else
                          ["legacy", "final", "JAVA_BACKEND", "AI_APPLICATION"])
        }
        summary[mode]["latencyMs"] = dict(zip(["p50", "p95"], np.percentile(timings[mode], [50, 95]).tolist()))
    regressions = {}
    if "keyword" in rows:
        for mode in modes:
            regressions[mode] = {
                str(k): [after["id"] for before, after in zip(rows["keyword"], rows[mode])
                         if set(before["ids"][:k]) & set(before["relevantIds"])
                         and not set(after["ids"][:k]) & set(after["relevantIds"])]
                for k in [3, 8]
            }
    report = {"config": config, "split": args.split, "summary": summary,
              "cost": {"modelLoadSeconds": load_seconds, "indexSeconds": index_seconds,
                       "cacheHit": cache_hit, "cacheBytes": args.cache.stat().st_size if encoder else 0,
                       "vectorBytes": vectors.nbytes if vectors is not None else 0,
                       "modelBytes": (args.model_dir / "model.onnx").stat().st_size if encoder else 0},
              "regressions": regressions, "rows": rows}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({"summary": summary, "cost": report["cost"], "regressions": regressions}, indent=2))


if __name__ == "__main__":
    main()
