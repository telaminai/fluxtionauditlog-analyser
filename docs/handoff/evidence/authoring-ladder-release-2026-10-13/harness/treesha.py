"""Tree digest: sha256 over sorted lines '<relative path>\\0<sha256 of file>\\n' (files only, symlinks as link text)."""
import hashlib, os, sys
from pathlib import Path
root = Path(sys.argv[1]); h = hashlib.sha256()
for p in sorted(root.rglob('*'), key=lambda p: str(p.relative_to(root))):
    rel = str(p.relative_to(root))
    if p.is_symlink(): d = hashlib.sha256(os.readlink(p).encode()).hexdigest()
    elif p.is_file(): d = hashlib.sha256(p.read_bytes()).hexdigest()
    else: continue
    h.update(f'{rel}\0{d}\n'.encode())
print(h.hexdigest())
