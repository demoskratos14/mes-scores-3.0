#!/usr/bin/env python3
"""Cuit le sprite du vaisseau d'Astéroïdes à partir du STL 5jet.stl : vue de dessus, éclairée, nez vers le haut.

Usage : python3 bake_ship.py 5jet.stl ship_jet.png
Le STL a son nez vers -X, Z vers le haut : en vue de dessus, l'image est le plan tourné d'un quart de tour
(colonne = Y, ligne = X) ; le sprite est un carré de 256 x 256 px, le vaisseau centré sur sa boîte englobante.
"""
import struct
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

SIZE = 256          # côté du sprite final
SS = 4              # suréchantillonnage pour lisser les bords
LIGHT = np.array([-0.45, -0.35, 0.82])   # d'où vient la lumière (repère du STL), vers le haut et l'avant-gauche
LIGHT = LIGHT / np.linalg.norm(LIGHT)


def read_stl(path):
    data = open(path, "rb").read()
    count = struct.unpack("<I", data[80:84])[0]
    if 84 + count * 50 != len(data):
        raise SystemExit("STL binaire attendu")
    rec = np.frombuffer(data[84:84 + count * 50], dtype=np.dtype([("n", "<3f4"), ("v", "<9f4"), ("a", "<u2")]))
    return rec["v"].reshape(-1, 3, 3).astype(np.float64)


def main(src, dst):
    tris = read_stl(src)
    mins = tris.reshape(-1, 3).min(0)
    maxs = tris.reshape(-1, 3).max(0)
    center = (mins + maxs) / 2
    span = max(maxs[0] - mins[0], maxs[1] - mins[1])
    pad = 0.02
    scale = SIZE * SS * (1 - 2 * pad) / span
    half = SIZE * SS / 2

    # Normales recalculées (celles du fichier sont à zéro) ; seuls les triangles tournés vers le haut sont dessinés.
    e1 = tris[:, 1] - tris[:, 0]
    e2 = tris[:, 2] - tris[:, 0]
    normals = np.cross(e1, e2)
    length = np.linalg.norm(normals, axis=1)
    keep = length > 1e-9
    tris, normals, length = tris[keep], normals[keep], length[keep]
    normals = normals / length[:, None]
    # Sens des triangles non garanti : on oriente chaque normale vers le haut (les faces cachées sont recouvertes).
    normals[normals[:, 2] < 0] *= -1

    order = np.argsort(tris[:, :, 2].mean(axis=1))  # du plus bas au plus haut : le peintre
    zmin, zmax = mins[2], maxs[2]

    img = Image.new("RGBA", (SIZE * SS, SIZE * SS), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    for i in order:
        t = tris[i]
        n = normals[i]
        if abs(n[2]) < 0.02:
            continue  # tranche vue de face : sans surface visible
        lam = max(0.0, float(n @ LIGHT))
        height = (t[:, 2].mean() - zmin) / (zmax - zmin)
        shade = 0.30 + 0.62 * lam + 0.18 * height
        shade = min(1.0, shade)
        base = np.array([172, 192, 218])         # acier bleuté
        tint = np.array([20, 8, -10]) * height     # les parties hautes tirent vers le clair chaud
        rgb = np.clip(base * shade + tint, 0, 255)
        pts = [(half + (p[1] - center[1]) * scale, half + (p[0] - center[0]) * scale) for p in t]
        draw.polygon(pts, fill=tuple(int(c) for c in rgb) + (255,))

    img = img.resize((SIZE, SIZE), Image.LANCZOS)
    img.save(dst, optimize=True)
    print("ok", dst, img.size)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
