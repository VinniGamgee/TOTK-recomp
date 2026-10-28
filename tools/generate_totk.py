"""Generate C locally; inputs and outputs must never be published."""
import argparse
from pathlib import Path
import subprocess
import sys
from prepare_runtime import ROOT

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('exefs', type=Path)
    p.add_argument('--runtime', type=Path, default=ROOT / 'work/runtime')
    p.add_argument('--output', type=Path, default=ROOT / 'work/generated')
    a = p.parse_args()
    subprocess.run([sys.executable, str(ROOT / 'tools/totk_dump.py'), str(a.exefs)], check=True)
    for name, base in [('main', '0x7000000000'), ('sdk', '0x7004000000'), ('subsdk0', '0x7005000000')]:
        cmd = [sys.executable, str(a.runtime / 'tools/gen.py'), name, str(a.exefs / name), base,
               str(a.output / name), '--simd', 'asm', '--dyn-base']
        if name == 'sdk':
            cmd += ['--hle', str(a.runtime / 'runtime/hle/hle_symbols.txt')]
        subprocess.run(cmd, check=True)
    print('C generated; compilation, linking and gameplay are separate unverified steps.')

if __name__ == '__main__':
    main()
