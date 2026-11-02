#!/usr/bin/env python3
"""Apply the MoonDump safe key validation fix to the unpacked source at build time."""
from pathlib import Path
import sys

source_dir=Path(sys.argv[1])
service_path=source_dir / "app/src/main/java/dev/moonwitch/moondump/ExtractService.java"
s=service_path.read_text(encoding="utf-8")
old='''        Files.copy(originalKeys.toPath(),keysFile.toPath(),StandardCopyOption.REPLACE_EXISTING);
        message("Chaves localizadas e preparadas no armazenamento privado do aplicativo.");'''
new='''        KeyNormalizer.Summary summary=KeyNormalizer.prepare(originalKeys, keysFile);
        message("Chaves validadas: "+summary.recognized+" reconhecidas; "+summary.ignored+
                " entradas extras ignoradas. Nenhum valor de chave e registrado.");'''
if old not in s:
    raise SystemExit("Expected key-copy code not found. Refusing to patch unexpected source.")
s=s.replace(old,new)
# Hactool may otherwise echo an invalid key value in its stderr; redact prior to logging.
original='''        return value.replaceAll("(?i)\\b[0-9a-f]{32,}\\b","[chave ocultada]");'''
replacement='''        return value.replaceAll("(?i)Key \\([^\\)]*\\) must be [0-9]+ hex digits!", "Formato de chave invalido!")
                .replaceAll("(?i)\\b[0-9a-f]{8,}\\b", "[chave ocultada]");'''
if original not in s:
    raise SystemExit("Expected redaction code not found. Refusing to patch unexpected source.")
s=s.replace(original,replacement)
service_path.write_text(s,encoding="utf-8")
print("MoonDump: safe prod.keys validator integrated")
