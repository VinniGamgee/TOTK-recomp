"""Create a private, patched checkout of the pinned LAS runtime. Never overwrite work."""
import argparse
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
PIN = '4572f06716a49207fdcc13d34a45f154644202f3'

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=ROOT / 'work/runtime')
    args = parser.parse_args()
    source = ROOT / 'vendor/las-runtime'
    if args.output.exists():
        parser.error(f'{args.output} already exists; choose a new directory')
    head = subprocess.check_output(['git', '-C', str(source), 'rev-parse', 'HEAD'], text=True).strip()
    if head != PIN:
        parser.error('submodule differs from the reviewed pin; run git submodule update --init')
    subprocess.run(['git', 'clone', '--no-hardlinks', str(source), str(args.output)], check=True)
    subprocess.run(['git', '-C', str(args.output), 'checkout', '--detach', PIN], check=True)
    patch = ROOT / 'patches/totk143.patch'
    subprocess.run(['git', '-C', str(args.output), 'apply', '--check', str(patch)], check=True)
    subprocess.run(['git', '-C', str(args.output), 'apply', str(patch)], check=True)
    print(f'Experimental TOTK runtime prepared at {args.output}; boot not verified.')

if __name__ == '__main__':
    main()
