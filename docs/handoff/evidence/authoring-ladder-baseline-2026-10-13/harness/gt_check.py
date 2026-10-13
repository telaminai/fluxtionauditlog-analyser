"""Ground-truth / control check: VALID + ORACLE exactly as score_ladder.py computes them. Usage: gt_check.py <rung> <base>"""
import json, sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent))
import score_ladder as s
rung, base = int(sys.argv[1]), Path(sys.argv[2])
root = s.project_root(base / 'project')
rc, out = s.run(['./validate.sh'], root, s.env_for(base), timeout=300)
print(json.dumps({'base': str(base), 'root': str(root), 'VALID': rc == 0, 'ORACLE': s.oracle(rung, base, root)}, default=str))
