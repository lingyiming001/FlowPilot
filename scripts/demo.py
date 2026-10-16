#!/usr/bin/env python3
"""Exercise the running MOCK environment through authenticated public APIs."""
from pathlib import Path
import argparse
import json
import time
import urllib.request
import urllib.error

root=Path(__file__).resolve().parents[1]
env=dict(line.split('=',1) for line in (root/'deploy/env/.env').read_text().splitlines() if line and not line.startswith('#'))
parser=argparse.ArgumentParser()
parser.add_argument('--scenario',choices=['query','refund'],default='query')
parser.add_argument('--approve',action='store_true',help='Authenticate as local demo supervisor and explicitly approve each action')
args=parser.parse_args()
base='http://127.0.0.1:'+env.get('WEB_PORT','18080')
token=None

def api(path,body=None):
    headers={'Content-Type':'application/json'}
    if token:headers['Authorization']='Bearer '+token
    data=None if body is None else json.dumps(body).encode()
    request=urllib.request.Request(base+'/api'+path,data=data,headers=headers)
    try:
        with urllib.request.urlopen(request,timeout=35) as response:
            result=json.load(response)
    except urllib.error.HTTPError as error:
        result=json.load(error)
        raise SystemExit(str(error.code)+': '+result.get('message','API request failed'))
    if not result.get('success'):raise SystemExit(result.get('message','API failed'))
    return result['data']

login=api('/auth/login',{'username':'supervisor','password':env['DEMO_PASSWORD']})
token=login['token']
info=api('/system/info')
if info['llmMode']!='FAKE' or info['integrationMode']!='mock':
    raise SystemExit('Refusing demo writes: this script only operates in Fake LLM + Mock integration mode.')
workflow=api('/mails/demo-'+args.scenario+'/analyze',{})['workflowId']
print('Workflow:',workflow,'[Fake LLM / Mock]')
deadline=time.monotonic()+45
approved=[]
while time.monotonic()<deadline:
    detail=api('/workflows/'+workflow)
    if detail['state']=='WAITING_APPROVAL':
        pending=[a for a in detail['approvals'] if a['status']=='PENDING']
        if not args.approve:
            print('WAITING_APPROVAL:',', '.join(a['action_type'] for a in pending))
            break
        for approval in pending:
            api('/approvals/'+approval['id']+'/approve',{'expectedVersion':approval['version'],'comment':'本地Mock演示：已核对具体操作快照与业务依据'})
            approved.append(approval['action_type'])
            print('Approved separately:',approval['action_type'])
    elif detail['state'] in ['COMPLETED','FAILED','MANUAL_REVIEW','REJECTED','CANCELLED']:
        print('Final state:',detail['state'])
        if detail['state']!='COMPLETED':raise SystemExit(1)
        break
    time.sleep(0.4)
else:
    raise SystemExit('Timed out waiting for persistent workflow scheduler')
print('Open:',base+'/workflows/'+workflow)
