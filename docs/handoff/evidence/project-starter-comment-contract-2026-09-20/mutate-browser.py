import os,subprocess
from pathlib import Path
source=Path('src/lib/starter/comment-contract.ts');original=source.read_bytes()
try:
 old='return `${contract.comments[key]}'
 assert original.decode().count(old)==1
 source.write_text(original.decode().replace(old,'return `MUTATED ${contract.comments[key]}',1))
 result=subprocess.run(['pnpm','vitest','run','src/lib/starter/comment-contract.test.ts'],capture_output=True,text=True)
 output=result.stdout+result.stderr
 Path('/private/tmp/journey-comments-seen-red.log').write_text(output)
 assert result.returncode!=0 and 'browser reference' in output and '1 failed | 2 passed' in output,output
 print('Seen red: packaged-resource comparison fails at browser reference; hash-equivalence check passes')
finally:source.write_bytes(original)
