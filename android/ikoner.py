import json
import re
import sys
from pathlib import Path

design = Path(sys.argv[1] if len(sys.argv) > 1 else "~/Code/design").expanduser()
android = Path(__file__).parent
tokens = json.loads((design / "themes/waddle/tokens.json").read_text())


def farge(navn):
    v = tokens["color"]
    for d in navn.split("."):
        v = v[d]
    v = v["$value"]
    return farge(v[7:-1]) if v.startswith("{") else v


def symbol(navn):
    return re.search(r' d="([^"]+)"', (design / f"build/icons/svg/{navn}.svg").read_text())[1]


def pingvin():
    svg = (design / "build/waddle/penguin/penguin-dark.svg").read_text()
    blekk, papir = farge("grey.0"), farge("grey.9")
    stier = []
    for fyll, d in re.findall(r'<path fill="([^"]+)" d="([^"]+)"', svg):
        if not fyll.startswith("url"):
            stier.append((fyll, d))
            continue
        stier.append((papir, d))
        celler = ""
        for x, y, b, h in re.findall(r"M(\d+) (\d+)h(\d+)v(\d+)", d):
            x, y, b, h = map(int, (x, y, b, h))
            celler += "".join(f"M{i} {j}h1v1h-1z" for j in range(y, y + h) for i in range(x, x + b) if i % 2 == j % 2)
        stier.append((blekk, celler))
    return stier


def sti(fyll, d):
    return f'            <path android:fillColor="{fyll}" android:pathData="{d}" />'


def vektor(innhold):
    return f"""<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
{innhold}
</vector>
"""


def gruppe(x, y, skala, innhold):
    return f"""        <group android:translateX="{x}" android:translateY="{y}" android:scaleX="{skala}" android:scaleY="{skala}">
{innhold}
        </group>"""


def merke(fyll, ikon):
    return "\n".join([gruppe(30, 14, 2, sti(fyll, symbol(ikon))), sti(fyll, "M48 66h12v4H48z")])


def flate(innhold):
    return vektor(f"""    <group android:translateX="18" android:translateY="18" android:scaleX="0.6667" android:scaleY="0.6667">
{innhold}
    </group>""")


blekk = farge("grey.0")
figur = gruppe(24, 74, 1, "\n".join(sti(f, d) for f, d in pingvin()))
for app in json.loads((android / "apper.json").read_text()):
    res = android / app["modul"] / "src/main/res"
    (res / "drawable").mkdir(parents=True, exist_ok=True)
    (res / "mipmap-anydpi-v26").mkdir(parents=True, exist_ok=True)
    (res / "drawable/ikon_plate.xml").write_text(vektor(f'    <path android:fillColor="{farge("ansi." + app["farge"])}" android:pathData="M0 0h108v108H0z" />'))
    (res / "drawable/ikon_forgrunn.xml").write_text(flate(merke(blekk, app["ikon"]) + "\n" + figur))
    (res / "drawable/ikon_symbol.xml").write_text(flate(merke("#ffffff", app["ikon"])))
    (res / "mipmap-anydpi-v26/ic_launcher.xml").write_text("""<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ikon_plate" />
    <foreground android:drawable="@drawable/ikon_forgrunn" />
    <monochrome android:drawable="@drawable/ikon_symbol" />
</adaptive-icon>
""")
    print(f"{app['modul']}: {app['ikon']} på {app['farge']}")
