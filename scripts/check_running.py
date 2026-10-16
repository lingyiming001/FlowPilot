#!/usr/bin/env python3
"""Authenticated read-only smoke checks for the local deployment (no business writes)."""
import json
import urllib.request
from pathlib import Path
root=Path(__file__).resolve().parents[1]
settings=dict(line.split('=',1) for line in (root/'deploy/env/.env').read_text().splitlines() if line and not line.startswith('#'))
base='http://localhost:'+settings.get('WEB_PORT','18080')
headers={'Content-Type':'application/json','Origin':base}
request=urllib.request.Request(base+'/api/auth/login',data=json.dumps({'username':'admin','password':settings['DEMO_PASSWORD']}).encode(),headers=headers)
with urllib.request.urlopen(request,timeout=35) as response:token=json.load(response)['data']['token']
headers['Authorization']='Bearer '+token
for path in ['/auth/me','/system/info','/dashboard','/mails','/orders','/workflows','/approvals','/audit-logs','/failed-tasks','/users','/roles']:
    request=urllib.request.Request(base+'/api'+path,headers=headers)
    with urllib.request.urlopen(request,timeout=35) as response:
        body=json.load(response)
        assert body['success'],path
        if path=='/workflows':workflows=body['data']
print('PASS: authenticated login and 11 read APIs; browser Origin preserved')
if workflows:
    workflow=workflows[0]['id']
    request=urllib.request.Request(base+'/api/workflows/'+workflow,headers=headers)
    with urllib.request.urlopen(request,timeout=35) as response:events=json.load(response)['data']['events']
    request=urllib.request.Request(base+'/api/workflows/'+workflow+'/events',headers=headers)
    with urllib.request.urlopen(request,timeout=8) as response:
        assert response.headers.get('Content-Type','').startswith('text/event-stream')
        assert response.readline().decode().startswith('id:')
    replay_headers={**headers,'Last-Event-ID':events[-1]['id']}
    request=urllib.request.Request(base+'/api/workflows/'+workflow+'/events',headers=replay_headers)
    with urllib.request.urlopen(request,timeout=8) as response:assert response.readline().decode().startswith(':')
    print('PASS: authenticated SSE and Last-Event-ID replay without duplicate history')
