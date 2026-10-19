import importlib.util
import json
from pathlib import Path
import subprocess
import types

ROOT = Path(__file__).resolve().parents[4]
spec = importlib.util.spec_from_file_location('test_integrity', ROOT / 'tools/test_m74_onboarding_integrity.py')
t = importlib.util.module_from_spec(spec)
spec.loader.exec_module(t)

def probe(name, change):
    case = t.RunIntegrityTest()
    case.setUp()
    try:
        change(case)
        rc, card, err = case.score()
        print(json.dumps({'probe': name, 'rc': rc, 'verdict': card and card['verdict'],
            'boundary': card and card['boundary'], 'claims': card and card['correctness']['claims'], 'err': err}))
    finally:
        case.doCleanups()

def answer(case):
    (case.run / 'answers.jsonl').write_text('{"ticket":1,"message":"never breached"}\n')

def add_fact(case, event, fields):
    p = case.run / 'session-record.yaml'
    with p.open('a') as out:
        out.write('\n---\neventLogRecord:\n event: '+event+'\n eventToString: '+event+'['+fields+']\n nodeLogs:\n---\n')
    case.edit('session-record.meta.json', lambda d: d.update(total=d['total']+1))

probe('answer-object-without-text', answer)
probe('unaccounted-current-review-decision', lambda c: add_fact(c, 'ProposalItemAnswered', 'proposalId=p-DEMO, itemId=DEMO-1, yes=true'))
probe('unaccounted-current-restart', lambda c: add_fact(c, 'LocalAgentRestartAnswered', 'request=99, offer=1, restart=true, consent=null'))

old = types.ModuleType('old_runner')
exec(compile(subprocess.check_output(['git','show','dd70ea80:tools/m74_onboarding_runner.py'], cwd=ROOT, text=True), 'old_runner', 'exec'), old.__dict__)
result = subprocess.run(['java', '-cp', str(ROOT / 'target/classes'), str(ROOT / 'tools/m74_record_pack_crosscheck/RecordPermissionFacts.java')], capture_output=True, text=True, check=True)
print(json.dumps({'probe': 'baseline-current-Java-approvals', 'approvals': old.approvals(old.parse_session_record(result.stdout))}))
