#!/usr/bin/env python3
"""Lê relatórios JUnit XML e publica o resultado como anotações do GitHub Actions
(visíveis na página do build, sem precisar baixar artefatos)."""
import glob
import os
import sys
import xml.etree.ElementTree as ET

label, root = sys.argv[1], sys.argv[2]
files = glob.glob(os.path.join(root, "**", "TEST-*.xml"), recursive=True)
total = failed = skipped = 0
failures = []
for f in files:
    for case in ET.parse(f).getroot().iter("testcase"):
        total += 1
        name = f"{case.get('classname', '').split('.')[-1]}.{case.get('name')}"
        bad = case.find("failure")
        if bad is None:
            bad = case.find("error")
        if bad is not None:
            failed += 1
            msg = (bad.get("message") or bad.text or "").strip().splitlines()
            failures.append((name, msg[0][:300] if msg else ""))
        elif case.find("skipped") is not None:
            skipped += 1

passed = total - failed - skipped
print(f"::notice title={label}: {passed}/{total} passaram::{passed} aprovados, {failed} falharam, {skipped} ignorados ({len(files)} arquivos)")
for name, msg in failures[:20]:
    clean = msg.replace("\n", " ").replace("::", ":")
    print(f"::error title=FALHOU {name}::{clean}")
if not files:
    print(f"::warning title={label}::nenhum relatório de teste encontrado em {root}")
