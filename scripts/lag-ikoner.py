#!/usr/bin/env python3
"""Lager PNG-ikonene til PWA-en (#47) fra samme figur som frontend/public/favicon.svg.

Ren Python uten avhengigheter: figuren er bare avrundede rektangler, som tegnes
rad for rad i fire ganger oppløsning og skaleres ned (kantutjevning).
Kjør fra repo-roten: python3 scripts/lag-ikoner.py
"""
import struct
import zlib
from pathlib import Path

UT = Path(__file__).resolve().parent.parent / "frontend" / "public"
BAKGRUNN = (0xF4, 0xF8, 0xFB)
KJOLESKAP = (0x0E, 0x74, 0x90)
LYS = (0xF4, 0xF8, 0xFB)
OVERSAMPLING = 4

# Figuren fra favicon.svg i et 64×64-rutenett: (x1, y1, x2, y2, hjørneradius, farge).
FIGUR = [
    (14, 4, 50, 60, 7, KJOLESKAP),
    (14, 24.5, 50, 27.5, 0, LYS),     # skillet mellom fryser og kjøl (strek, 3 bred)
    (19, 11, 22, 20, 1.5, LYS),       # håndtak oppe
    (19, 32, 22, 46, 1.5, LYS),       # håndtak nede
]


def tegn(storrelse: int, figurandel: float) -> bytes:
    """Kvadratisk ikon; figurandel er figurens høyde (56 av 64 enheter) som andel av ikonet."""
    s = storrelse * OVERSAMPLING
    skala = figurandel * s / 56
    dx = s / 2 - 32 * skala
    dy = s / 2 - 32 * skala
    lerret = [bytearray(bytes(BAKGRUNN) * s) for _ in range(s)]
    for x1, y1, x2, y2, r, farge in FIGUR:
        X1, Y1, X2, Y2, R = x1 * skala + dx, y1 * skala + dy, x2 * skala + dx, y2 * skala + dy, r * skala
        piksel = bytes(farge)
        for py in range(max(0, int(Y1)), min(s, int(Y2) + 1)):
            y = py + 0.5
            if not Y1 <= y <= Y2:
                continue
            innrykk = 0.0
            if R > 0:
                # Avstand inn fra kanten i hjørnene (sirkelbue).
                for senter in (Y1 + R, Y2 - R):
                    if (y < Y1 + R and senter == Y1 + R) or (y > Y2 - R and senter == Y2 - R):
                        d = abs(y - senter)
                        innrykk = R - (R * R - d * d) ** 0.5
            a = max(0, int(X1 + innrykk + 0.5))
            b = min(s, int(X2 - innrykk + 0.5))
            if b > a:
                lerret[py][a * 3:b * 3] = piksel * (b - a)
    # Skaler ned: snitt av OVERSAMPLING×OVERSAMPLING piksler.
    n = OVERSAMPLING
    rader = []
    for oy in range(storrelse):
        rad = bytearray([0])  # PNG-filtertype 0
        kilde = lerret[oy * n:(oy + 1) * n]
        for ox in range(storrelse):
            for k in range(3):
                total = 0
                for r in kilde:
                    base = ox * n * 3 + k
                    total += sum(r[base + i * 3] for i in range(n))
                rad.append(total // (n * n))
        rader.append(bytes(rad))
    return png(storrelse, b"".join(rader))


def png(storrelse: int, data: bytes) -> bytes:
    def bit(type_: bytes, innhold: bytes) -> bytes:
        return struct.pack(">I", len(innhold)) + type_ + innhold + struct.pack(">I", zlib.crc32(type_ + innhold))
    ihdr = struct.pack(">IIBBBBB", storrelse, storrelse, 8, 2, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + bit(b"IHDR", ihdr) + bit(b"IDAT", zlib.compress(data, 9)) + bit(b"IEND", b"")


IKONER = {
    # Vanlige ikoner: figuren fyller det meste.
    "ikon-192.png": (192, 0.78),
    "ikon-512.png": (512, 0.78),
    # Maskable: Android kan klippe ikonet til sirkel; figuren holdes innenfor den trygge sonen (80 %).
    "ikon-maskable-512.png": (512, 0.6),
    # iOS runder hjørnene selv og vil ha et ikon uten gjennomsiktighet.
    "apple-touch-icon.png": (180, 0.74),
}

if __name__ == "__main__":
    for navn, (storrelse, andel) in IKONER.items():
        (UT / navn).write_bytes(tegn(storrelse, andel))
        print(f"{navn}: {storrelse}×{storrelse}")
