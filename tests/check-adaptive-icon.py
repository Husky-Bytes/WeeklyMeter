#!/usr/bin/env python3
"""Static launcher resource/geometry regression tests; not on-device rendering."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

RES = Path(__file__).resolve().parents[1] / "app/src/main/res"
A = "{http://schemas.android.com/apk/res/android}"
checks = 0


def check(condition, message):
    global checks
    assert condition, message
    checks += 1


for api, expected_tags in ((26, ["background", "foreground"]),
                           (33, ["background", "foreground", "monochrome"])):
    icon = ET.parse(RES / f"mipmap-anydpi-v{api}/ic_launcher.xml").getroot()
    check(icon.tag == "adaptive-icon", f"API {api}: adaptive drawable required")
    check([child.tag for child in icon] == expected_tags, f"API {api}: expected layers")
    for layer in icon:
        expected = "ic_launcher_background" if layer.tag == "background" else "ic_launcher_foreground"
        check(layer.get(A + "drawable") == f"@drawable/{expected}", f"API {api}: {layer.tag} reference")

background = ET.parse(RES / "drawable/ic_launcher_background.xml").getroot()
foreground = ET.parse(RES / "drawable/ic_launcher_foreground.xml").getroot()
for layer in (background, foreground):
    check(layer.tag == "vector", "scalable vector layer")
    for dimension in ("width", "height"):
        check(layer.get(A + dimension) == "108dp", f"108dp {dimension}")
    for dimension in ("viewportWidth", "viewportHeight"):
        check(layer.get(A + dimension) == "108", f"108-unit {dimension}")
    check([child.tag for child in layer] == ["path"], "one clean path, no baked mask/shadow")

bg_path = background.find("path")
check(bg_path.get(A + "fillColor") == "#18222D", "original background identity")
check(bg_path.get(A + "pathData") == "M0,0h108v108h-108z", "full-bleed background")
fg_path = foreground.find("path")
check(fg_path.get(A + "fillColor") == "#B8EFCF", "original mint mark identity")
path = fg_path.get(A + "pathData")
pattern = r"M(\d+),(\d+)h(\d+)v(\d+)h-(\d+)z"
bars = re.findall(pattern, path)
check(len(bars) == 3 and "".join(re.findall(r"M[^M]+", path)) == path,
      "three rectangular bars")
check("".join(match.group(0) for match in re.finditer(pattern, path)) == path,
      "all mark geometry checked")
previous_height = 0
for raw in bars:
    x, y, width, height, return_width = map(int, raw)
    check(width == return_width, "closed bar")
    check(height > previous_height, "ascending usage-meter bars")
    check(y + height == 78, "aligned baseline")
    previous_height = height
    for corner_x, corner_y in ((x, y), (x + width, y), (x, y + height), (x + width, y + height)):
        check((corner_x - 54) ** 2 + (corner_y - 54) ** 2 <= 33 ** 2,
              "mark remains inside centered 66dp safe circle")

legacy = ET.parse(RES / "drawable/ic_meter.xml").getroot()
check(len(legacy.findall("path")) == 2, "existing notification icon stays separate")
check(legacy.findall("path")[1].get(A + "pathData") ==
      "M25,69h12v15h-12zM48,47h12v37h-12zM71,24h12v60h-12z",
      "legacy notification artwork remains unchanged")
print(f"PASS: {checks} adaptive-icon resource and safe-zone checks. No device rendering verification.")
