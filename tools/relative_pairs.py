"""Private relocation oracle for tests/loader_probe.c; never publish the output."""
import argparse
from pathlib import Path
import struct
import sys
from prepare_runtime import ROOT

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('exefs', type=Path)
p.add_argument('output', type=Path)
p.add_argument('--runtime', type=Path, default=ROOT / 'work/runtime')
a = p.parse_args()
sys.path.insert(0, str(a.runtime / 'tools'))
from nso import Nso, R_AARCH64_RELATIVE
with a.output.open('xb') as out:
    for name, base in [('main', 0x7000000000), ('sdk', 0x7004000000), ('subsdk0', 0x7005000000)]:
        for off, kind, _, add in Nso(str(a.exefs / name)).relocs:
            if kind == R_AARCH64_RELATIVE:
                out.write(struct.pack('<QQ', base + off, base + add))
