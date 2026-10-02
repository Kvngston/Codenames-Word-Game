#!/usr/bin/env python3
"""Builds src/main/resources/vectors/glove-50d.bin.gz from GloVe for WordVectors.java.

Download glove.6B.zip from https://nlp.stanford.edu/projects/glove/ (862 MB), unzip
glove.6B.50d.txt, then run from server/:

    python3 scripts/build-word-vectors.py path/to/glove.6B.50d.txt

Keeps the most common plain words plus every part of every built-in pack word,
each vector scaled to fill a signed byte. Format (big-endian, gzipped): int count,
int dimensions, then per word a Java writeUTF string and `dimensions` signed bytes.
"""
import gzip
import pathlib
import re
import struct
import sys

COMMON = 40_000
WORD = re.compile(r"[a-z]+(?:['-][a-z]+)*")
HERE = pathlib.Path(__file__).resolve().parent.parent
PACKS = HERE / "src/main/resources/packs"
OUT = HERE / "src/main/resources/vectors/glove-50d.bin.gz"


def pack_tokens():
    tokens = set()
    for path in PACKS.glob("*.txt"):
        for line in path.read_text().splitlines():
            word = line.strip().lower()
            if not word or word.startswith("#"):
                continue
            tokens.add(word.replace(" ", "-"))
            tokens.update(re.split(r"[ -]+", word))
    return tokens


def main(glove):
    wanted = pack_tokens()
    kept = []
    common = 0
    with open(glove, encoding="utf8") as lines:
        for line in lines:
            word, *values = line.rstrip().split(" ")
            if not WORD.fullmatch(word):
                continue
            if common < COMMON or word in wanted:
                common += 1
                vector = [float(v) for v in values]
                scale = 127 / max(abs(v) for v in vector)
                kept.append((word, bytes(round(v * scale) & 0xFF for v in vector)))
    missing = sorted(t for t in wanted if t not in {w for w, _ in kept} and "-" not in t)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with gzip.open(OUT, "wb", compresslevel=9) as out:
        out.write(struct.pack(">ii", len(kept), len(kept[0][1])))
        for word, vector in kept:
            encoded = word.encode("utf8")
            out.write(struct.pack(">H", len(encoded)) + encoded + vector)
    print(f"{len(kept)} words -> {OUT} ({OUT.stat().st_size // 1024} KB)")
    if missing:
        print("pack words with no vector (they fall back to sub-themes):", " ".join(missing))


if __name__ == "__main__":
    main(sys.argv[1])
