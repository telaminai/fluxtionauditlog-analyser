#!/usr/bin/env python3
"""Regression guard: healthy startup is insufficient after the server stops."""
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch, Mock
from types import SimpleNamespace

spec = importlib.util.spec_from_file_location('watch', Path(__file__).with_name('analyser-session-watch.py'))
watch = importlib.util.module_from_spec(spec)
spec.loader.exec_module(watch)

SERVER = '''import http.server,json
class H(http.server.BaseHTTPRequestHandler):
 def do_GET(self):
  self.send_response(200);self.end_headers();self.wfile.write(b'{"verbs":["context"]}')
 def log_message(self,*args):pass
s=http.server.HTTPServer(('127.0.0.1',0),H)
print(s.server_port,flush=True)
s.serve_forever()
'''

class SessionWatchTest(unittest.TestCase):
    def test_real_server_then_exit_cannot_remain_healthy(self):
        with tempfile.TemporaryDirectory() as temp:
            endpoint = Path(temp)/'endpoint'
            server = subprocess.Popen([sys.executable,'-u','-c',SERVER],stdout=subprocess.PIPE,text=True)
            try:
                port = int(server.stdout.readline())
                endpoint.write_text(json.dumps({'pid':server.pid,'url':f'http://127.0.0.1:{port}','token':'test'}))
                self.assertEqual('ok',watch.health(server,endpoint)['manifest'])
                server.terminate();server.wait(timeout=10)
                with self.assertRaisesRegex(RuntimeError,'analyser exited'):
                    watch.health(server,endpoint)
            finally:
                watch.stop(server)
                server.stdout.close()

    def test_missing_endpoint_refuses(self):
        class Process:
            pid=42
            def poll(self):return None
        with tempfile.TemporaryDirectory() as temp:
            with self.assertRaisesRegex(RuntimeError,'endpoint missing'):
                watch.health(Process(),Path(temp)/'missing')

    def test_foreign_endpoint_refuses_before_http(self):
        class Process:
            pid=42
            def poll(self):return None
        with tempfile.TemporaryDirectory() as temp:
            endpoint=Path(temp)/'endpoint'
            endpoint.write_text(json.dumps({'pid':43}))
            with self.assertRaisesRegex(RuntimeError,'another process'):
                watch.health(Process(),endpoint)

    def test_live_process_with_unreachable_endpoint_refuses(self):
        class Process:
            pid=42
            def poll(self):return None
        with tempfile.TemporaryDirectory() as temp:
            endpoint=Path(temp)/'endpoint'
            endpoint.write_text(json.dumps({'pid':42,'url':'http://127.0.0.1:1','token':'test'}))
            with patch.object(watch.urllib.request,'urlopen',side_effect=OSError('offline')):
                with self.assertRaisesRegex(RuntimeError,'manifest request failed'):
                    watch.health(Process(),endpoint)

    def test_supervision_stops_client_when_health_is_lost(self):
        with tempfile.TemporaryDirectory() as temp:
            server=Mock(pid=41,returncode=0);server.poll.return_value=None
            client=Mock(pid=42);client.poll.return_value=None
            args=SimpleNamespace(output=Path(temp)/'observer',home=Path(temp)/'home',
                java='java',jar=Path('test.jar'),command=['client'],seconds=0)
            with patch.object(watch.subprocess,'Popen',side_effect=[server,client]), \
                 patch.object(watch,'health',side_effect=[{'pid':41,'manifest':'ok'},
                     RuntimeError('analyser exited: 0')]), \
                 patch.object(watch,'stop') as stop:
                self.assertEqual(1,watch.run(args))
                self.assertEqual([client,server],[c.args[0] for c in stop.call_args_list])
            events=[json.loads(x) for x in (args.output/'health.jsonl').read_text().splitlines()]
            failures=[e for e in events if e['kind']=='failure']
            self.assertEqual('analyser exited: 0',failures[0]['error'])
            self.assertFalse(any(e['kind']=='client-ended' for e in events))

    def test_explicit_exchange_setup_preserves_other_settings(self):
        with tempfile.TemporaryDirectory() as temp:
            home=Path(temp)/'home';exchange=Path(temp)/'exchange'
            config=home/'.fluxtion-analyser/config';config.parent.mkdir(parents=True)
            config.write_text('assistant.exports=false\nassistant.exportDir=old\nsourceRoots=keep\n')
            watch.configure_exchange(home,exchange)
            text=config.read_text()
            self.assertIn('assistant.exports=true\n',text)
            self.assertIn('assistant.exportDir='+str(exchange.resolve())+'\n',text)
            self.assertIn('sourceRoots=keep\n',text)
            self.assertEqual(1,text.count('assistant.exports='))
            self.assertTrue(exchange.is_dir())

if __name__=='__main__':unittest.main()
