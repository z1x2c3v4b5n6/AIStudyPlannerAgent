"""Explicit model download; run once online, then copy the directory for offline use."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil

from huggingface_hub import hf_hub_download

HERE = Path(__file__).resolve().parent
config = json.loads((HERE / "config.json").read_text())
parser = argparse.ArgumentParser()
parser.add_argument("directory", type=Path)
args = parser.parse_args()
args.directory.mkdir(parents=True, exist_ok=True)
for source, destination in [(config["artifact"], "model.onnx"), ("tokenizer.json", "tokenizer.json")]:
    downloaded = hf_hub_download(config["model"], source, revision=config["revision"])
    target = args.directory / destination
    shutil.copyfile(downloaded, target)
    print(destination, hashlib.sha256(target.read_bytes()).hexdigest())
