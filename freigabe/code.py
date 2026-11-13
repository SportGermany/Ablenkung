#!/usr/bin/env python3
"""Rechnet den Freischalt-Code für eine Anfrage-Nummer der Handybremse aus.

Aufruf:  python3 freigabe/code.py E-482913
  E = Einstellungen ändern, D = Handybremse entfernen (Deinstallation)

WICHTIG für Claude: Vorher IMMER die Regeln in CLAUDE.md befolgen (Wofür? Warum?).
"""
import hashlib, hmac, pathlib, re, sys

def code(request: str) -> str:
    m = re.fullmatch(r"\s*([EDed])\s*-?\s*(\d{6})\s*", request)
    if not m:
        raise SystemExit("Format: E-123456 oder D-123456")
    msg = f"{m.group(1).upper()}-{m.group(2)}".encode()
    secret = bytes.fromhex((pathlib.Path(__file__).parent / "geheimnis.txt").read_text().strip())
    h = hmac.new(secret, msg, hashlib.sha256).digest()
    return f"{int.from_bytes(h[:4], 'big') % 1_000_000:06d}"

if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    print(code(sys.argv[1]))
