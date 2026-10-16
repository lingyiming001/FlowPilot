#!/usr/bin/env python3
from pathlib import Path
import secrets
import base64
root=Path(__file__).resolve().parents[2]
p=root/'deploy/env/.env'
if not p.exists():
    content=(p.parent/'.env.example').read_text()
    for key in ['MYSQL_ROOT_PASSWORD','DB_PASSWORD','REDIS_PASSWORD','DEMO_PASSWORD']:
        content=content.replace(key+'=\n',key+'='+secrets.token_urlsafe(24)+'\n')
    for key in ['JWT_SECRET','ENCRYPTION_KEY']:
        content=content.replace(key+'=\n',key+'='+base64.b64encode(secrets.token_bytes(32)).decode()+'\n')
    p.write_text(content)
    p.chmod(0o600)
    print('已生成 deploy/env/.env（仅本机可读，已被 Git 忽略）')
else:
    print('使用已有 deploy/env/.env')
values=dict(line.split('=',1) for line in p.read_text().splitlines() if line and not line.startswith('#'))
if values.get('DEMO_ENABLED')=='true':
    access=p.parent/'ACCESS.local.md'
    access.write_text('# FlowPilot 本地演示登录\n\n地址：http://localhost:'+values.get('WEB_PORT','18080')+'\n\n账号：`admin`（另有 supervisor / operator / auditor）\n\n密码：`'+values.get('DEMO_PASSWORD','')+'`\n\n默认 Fake LLM + Mock 外部业务，仅作本地演示。此文件已被 Git 忽略，请勿上传。\n')
    access.chmod(0o600)
