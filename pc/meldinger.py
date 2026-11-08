#!/usr/bin/env python3
import base64
import hashlib
import json
import os
import subprocess
import sys
import urllib.request
from pathlib import Path

from cryptography.hazmat.primitives.ciphers.aead import AESGCM

OPPSETT = Path(os.environ.get("XDG_CONFIG_HOME", Path.home() / ".config")) / "meldinger"
ENHET = os.uname().nodename


def kobling():
    fil = OPPSETT / "kobling"
    if not fil.exists():
        sys.exit(f"Mangler kobling. Kopier den fra appen og kjør: meldinger.py koble <kobling>")
    return fil.read_text().strip()


def nokkel(lenke):
    adresse, passord = lenke.split("#", 1)
    salt = adresse.rsplit("/", 1)[1].encode()
    return adresse, hashlib.pbkdf2_hmac("sha256", passord.encode(), salt, 100_000, 32)


def send(tekst):
    adresse, k = nokkel(kobling())
    iv = os.urandom(12)
    innhold = json.dumps({"fra": ENHET, "tekst": tekst}).encode()
    data = base64.b64encode(iv + AESGCM(k).encrypt(iv, innhold, None))
    urllib.request.urlopen(urllib.request.Request(adresse, data=data, method="POST"), timeout=10)


def les(siste=None):
    adresse, k = nokkel(kobling())
    meldinger = []
    with urllib.request.urlopen(f"{adresse}/json?poll=1&since=all", timeout=20) as svar:
        for linje in svar.read().decode().splitlines():
            o = json.loads(linje)
            if o.get("event") != "message":
                continue
            try:
                rå = base64.b64decode(o["message"])
                innhold = json.loads(AESGCM(k).decrypt(rå[:12], rå[12:], None))
            except Exception:
                continue
            meldinger.append(innhold)
    return meldinger[-siste:] if siste else meldinger


def kopier(tekst):
    for kommando in (["wl-copy"], ["xclip", "-selection", "clipboard"], ["pbcopy"]):
        try:
            subprocess.run(kommando, input=tekst.encode(), check=True)
            return
        except (FileNotFoundError, subprocess.CalledProcessError):
            continue
    sys.exit("Fant ikke wl-copy, xclip eller pbcopy.")


def utklipp():
    for kommando in (["wl-paste", "--no-newline"], ["xclip", "-selection", "clipboard", "-o"], ["pbpaste"]):
        try:
            return subprocess.run(kommando, capture_output=True, check=True).stdout.decode()
        except (FileNotFoundError, subprocess.CalledProcessError):
            continue
    sys.exit("Fant ikke wl-paste, xclip eller pbpaste.")


def main():
    if len(sys.argv) < 2:
        sys.exit("Bruk: meldinger.py koble <kobling> | send <tekst> | send - | utklipp | les | kopier")
    kommando, resten = sys.argv[1], sys.argv[2:]
    if kommando == "koble":
        OPPSETT.mkdir(parents=True, exist_ok=True)
        fil = OPPSETT / "kobling"
        fil.write_text(resten[0] + "\n")
        fil.chmod(0o600)
    elif kommando == "send":
        send(sys.stdin.read() if resten == ["-"] else " ".join(resten))
    elif kommando == "utklipp":
        send(utklipp())
    elif kommando == "les":
        for m in les():
            print(f"{m.get('fra', '?')}: {m.get('tekst', '')}")
    elif kommando == "kopier":
        siste = les(1)
        if not siste:
            sys.exit("Ingen meldinger de siste 12 timene.")
        kopier(siste[0]["tekst"])
    else:
        sys.exit(f"Ukjent kommando: {kommando}")


if __name__ == "__main__":
    main()
