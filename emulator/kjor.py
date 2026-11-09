import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

UT = sys.argv[2] if len(sys.argv) > 2 else "ut"
os.makedirs(UT, exist_ok=True)
logg = open(os.path.join(UT, "logg.txt"), "w")


def adb(*a, tekst=True):
    r = subprocess.run(["adb", *a], capture_output=True, text=tekst, timeout=120)
    return r.stdout


def skriv(s):
    print(s)
    logg.write(s + "\n")
    logg.flush()


def skjerm():
    adb("shell", "uiautomator", "dump", "/sdcard/u.xml")
    xml = adb("shell", "cat", "/sdcard/u.xml")
    try:
        return ET.fromstring(xml[xml.index("<"):])
    except Exception:
        return None


def finn(rot, tekst, eksakt=False):
    if rot is None:
        return None
    beste = None
    for n in rot.iter("node"):
        felt = [(n.get("text") or ""), (n.get("content-desc") or "")]
        lik = any(f.strip().lower() == tekst.lower() for f in felt)
        if eksakt and not lik:
            continue
        if not lik and not any(tekst.lower() in f.lower() for f in felt):
            continue
        m = re.findall(r"\d+", n.get("bounds", ""))
        if len(m) == 4:
            x1, y1, x2, y2 = map(int, m)
            kandidat = ((x1 + x2) // 2, (y1 + y2) // 2, 0 if lik else len("".join(felt)))
            if beste is None or kandidat[2] < beste[2]:
                beste = kandidat
    return beste


def struktur(rot, dybde=0, maks=6):
    if rot is None or dybde > maks:
        return
    for n in rot:
        if n.tag != "node":
            continue
        skriv("  " + "  " * dybde + f"{n.get('class', '').split('.')[-1]} {n.get('bounds')} {(n.get('text') or '')[:30]}")
        struktur(n, dybde + 1, maks)


def bilde(navn):
    data = subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True, timeout=60).stdout
    open(os.path.join(UT, f"{navn}.png"), "wb").write(data)
    rot = skjerm()
    if rot is not None:
        tekster = [n.get("text") for n in rot.iter("node") if n.get("text")]
        skriv(f"  [{navn}] " + " | ".join(tekster)[:1500])


def rydd():
    for _ in range(3):
        rot = skjerm()
        if rot is None:
            return
        tekst = " ".join((n.get("text") or "") for n in rot.iter("node"))
        if "isn't responding" not in tekst and "keeps stopping" not in tekst:
            return
        punkt = finn(rot, "Wait") or finn(rot, "Close app")
        if not punkt:
            return
        skriv("  lukker systemdialog")
        adb("shell", "input", "tap", str(punkt[0]), str(punkt[1]))
        time.sleep(2)


while adb("shell", "getprop", "sys.boot_completed").strip() != "1":
    time.sleep(2)
time.sleep(25)
adb("shell", "input", "keyevent", "82")
adb("shell", "input", "keyevent", "3")
time.sleep(5)
rydd()

for linje in open(sys.argv[1]):
    linje = linje.strip()
    if not linje or linje.startswith("#"):
        continue
    kommando, _, arg = linje.partition(" ")
    skriv(f"> {linje}")
    if kommando in ("trykk", "bilde", "skriv"):
        rydd()
    if kommando == "apne":
        adb("shell", "monkey", "-p", arg, "-c", "android.intent.category.LAUNCHER", "1")
    elif kommando == "lenke":
        adb("shell", "am", "start", "-a", "android.intent.action.VIEW", "-d", arg)
    elif kommando == "lenkefil":
        for _ in range(60):
            if os.path.exists(arg):
                break
            time.sleep(1)
        lenke = open(arg).read().strip()
        adb("shell", "am", "start", "-a", "android.intent.action.VIEW", "-d", "'" + lenke + "'")
    elif kommando == "trykk":
        punkt = None
        for _ in range(5):
            punkt = finn(skjerm(), arg, eksakt=True)
            if punkt:
                break
            adb("shell", "input", "swipe", "540", "1500", "540", "700", "300")
            time.sleep(1)
        if not punkt:
            adb("shell", "input", "swipe", "540", "700", "540", "2000", "200")
            adb("shell", "input", "swipe", "540", "700", "540", "2000", "200")
            time.sleep(1)
            punkt = finn(skjerm(), arg)
        if punkt:
            adb("shell", "input", "tap", str(punkt[0]), str(punkt[1]))
        else:
            skriv(f"  fant ikke «{arg}»")
    elif kommando == "skriv":
        adb("shell", "input", "text", arg.replace(" ", "%s"))
    elif kommando == "tilbake":
        adb("shell", "input", "keyevent", "4")
    elif kommando == "vent":
        time.sleep(float(arg))
    elif kommando == "ventpa":
        tekst, _, sek = arg.rpartition(" ")
        slutt = time.time() + float(sek)
        while time.time() < slutt:
            rot = skjerm()
            if rot is not None and any(tekst.lower() in (n.get("text") or "").lower() for n in rot.iter("node")):
                break
            time.sleep(3)
        skriv(f"  ventet {int(float(sek) - (slutt - time.time()))} s på «{tekst}»")
    elif kommando == "sveip":
        if arg == "opp":
            adb("shell", "input", "swipe", "540", "1600", "540", "500", "300")
        else:
            adb("shell", "input", "swipe", "540", "500", "540", "1600", "300")
    elif kommando == "bilde":
        bilde(arg)
    elif kommando == "struktur":
        struktur(skjerm())
    elif kommando == "kjor":
        skriv(adb("shell", *arg.split(" ")))

krasj = adb("logcat", "-d", "-b", "crash")
open(os.path.join(UT, "krasj.txt"), "w").write(krasj if krasj.strip() else "ingen\n")
open(os.path.join(UT, "logcat.txt"), "w").write(adb("logcat", "-d", "-t", "3000"))
skriv("krasj: " + ("ingen" if not krasj.strip() else str(len(krasj.splitlines())) + " linjer"))
