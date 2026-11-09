#!/usr/bin/env python3
"""F0.5 deterministic sign-in acceptance. Synthetic loopback service; never reads credential contents.
Usage: JAVA_HOME=<JDK21> python3 tools/bench/compiler-sign-in.py <candidate-analyser.jar>
Requires the candidate Maven plugin 1.3.5-SNAPSHOT installed locally. No hosted compile or LLM billing.
"""
import json, os, queue, subprocess, sys, tempfile, threading, time
from pathlib import Path
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

root = Path(tempfile.mkdtemp(prefix='compiler-sign-in-bench-'))
approved = threading.Event()
TOKEN = 'synthetic-token-not-for-production'
DEVICE = 'synthetic-device-code-not-for-output'
class Service(BaseHTTPRequestHandler):
    def log_message(self, *args): pass
    def reply(self, status, body):
        data=json.dumps(body).encode(); self.send_response(status)
        self.send_header('Content-Type','application/json'); self.send_header('Content-Length',str(len(data)))
        self.end_headers(); self.wfile.write(data)
    def do_POST(self):
        self.rfile.read(int(self.headers.get('Content-Length',0)))
        if self.path=='/auth/device/authorize':
            self.reply(200,dict(device_code=DEVICE,user_code='DEMO-CODE',verification_uri='https://compiler.example.com/device',expires_in=30,interval=1))
        elif self.path=='/auth/device/token':
            if not approved.is_set(): self.reply(400,{'error':'authorization_pending'})
            else: self.reply(200,dict(access_token=TOKEN,credential_id='cred-demo',account_id='acct-demo'))
        else: self.reply(404,{})
    def do_GET(self):
        if self.headers.get('Authorization')!='Bearer '+TOKEN: self.reply(401,{}); return
        if self.path.startswith('/v1/me'): self.reply(200,{'selectedAccount':'acct-demo'})
        elif self.path=='/v1/usage': self.reply(200,dict(accountId='acct-demo',active=True,plan='free',available=5))
        else: self.reply(404,{})
    def do_DELETE(self): self.reply(200,{})
server=ThreadingHTTPServer(('127.0.0.1',0),Service)
threading.Thread(target=server.serve_forever,daemon=True).start()
origin='http://127.0.0.1:'+str(server.server_port)
env={k:v for k,v in os.environ.items() if not k.startswith(('FLUXTION_','MAVEN_','JAVA_TOOL_OPTIONS','_JAVA_OPTIONS'))}
# Every product credential read goes to this synthetic profile. Never inspect the resulting credential file.
home=root/'home'; home.mkdir(); store=home/'.fluxtion'/'credentials'
settings=root/'settings.xml'; settings.write_text('<settings/>')
java=Path(os.environ['JAVA_HOME'])/'bin'/'java'
process=subprocess.Popen([str(java),'-Duser.home='+str(home),'-Dfluxtion.credentialsFile='+str(store),
    '-Dfluxtion.analyser.legacyStoreFile='+str(home/'legacy-empty'),
    '-Dfluxtion.login.verificationOrigin=https://compiler.example.com','-jar',str(Path(sys.argv[1]).resolve()),'--mcp'],
    cwd=root,env=env,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
responses=queue.Queue(); stderr=[]; transcript=[]
def collect():
    for line in process.stdout: responses.put(line)
threading.Thread(target=collect,daemon=True).start()
stderr_thread=threading.Thread(target=lambda:stderr.extend(process.stderr),daemon=True)
stderr_thread.start()
sequence=0
def call(name,args):
    global sequence
    sequence+=1
    process.stdin.write(json.dumps(dict(jsonrpc='2.0',id=sequence,method='tools/call',params=dict(name=name,arguments=args)))+'\n')
    process.stdin.flush(); line=responses.get(timeout=20)
    assert TOKEN not in line and DEVICE not in line
    r=json.loads(line); assert r['id']==sequence and 'error' not in r,r
    data=r['result']['structuredContent']; transcript.append(data); return data
try:
    pending=call('compiler_sign_in_start',{'compilerOrigin':origin})
    assert pending['event']=='pending' and pending['userCode']=='DEMO-CODE',pending
    assert pending['verificationUri']=='https://compiler.example.com/device'
    # F0.5: approval is withheld until the client has received a usable, validated URL and user code.
    approved.set(); deadline=time.monotonic()+30
    while time.monotonic()<deadline:
        result=call('compiler_sign_in_status',{'attemptId':pending['attemptId']})
        if result['event']!='pending': break
        time.sleep(.1)
    assert result['event']=='signed_in' and result['accountId']=='acct-demo',result
    assert result['effectiveBuildIdentity']=='not-observed' and store.exists()
    # Verify the build client's own resolution, rather than interpreting MCP success as proof about Maven.
    run=subprocess.run(['mvn','-o','-B','-ntp','-s',str(settings),
        'com.telamin.fluxtion:fluxtion-maven-plugin:1.3.5-SNAPSHOT:account',
        '-Dfluxtion.compilerUrl='+origin,'-Dfluxtion.credentialsFile='+str(store)],
        cwd=root,env=env,capture_output=True,text=True,timeout=60)
    output=run.stdout+run.stderr
    assert TOKEN not in output and DEVICE not in output
    assert run.returncode==0 and 'Account ID (server verified): acct-demo' in output,output[-2000:]
    (root/'maven-account.log').write_text(output)
    result={'F0.5_handed_working_sign_in':True,'synthetic_approval':True,'browser_exercised':False,'shared_credential_saved':True,
            'maven_identity_verified':True,'hosted_generation_exercised':False,'real_llm_exercised':False}
    (root/'result.json').write_text(json.dumps(result,indent=2)+'\n')
    (root/'public-transcript.json').write_text(json.dumps(transcript,indent=2)+'\n')
    print(json.dumps(result)); print('Evidence:',root)
finally:
    process.stdin.close()
    try: process.wait(timeout=10)
    except subprocess.TimeoutExpired: process.kill(); process.wait()
    server.shutdown(); server.server_close()
    stderr_thread.join(timeout=10)
    assert not stderr_thread.is_alive(), "stderr collector did not finish; evidence is incomplete"
    text=''.join(stderr)
    assert TOKEN not in text and DEVICE not in text
    (root/'stderr.log').write_text(text)
