#!/usr/bin/env python3
"""Start local Java backend with ignored deployment environment; never print secrets."""
from pathlib import Path
import os
import subprocess
root=Path(__file__).resolve().parents[1]
env=os.environ.copy()
for line in (root/'deploy/env/.env').read_text().splitlines():
    if line and not line.startswith('#'):
        key,value=line.split('=',1)
        env[key]=value
java_home=Path('/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home')
java=str(java_home/'bin/java') if java_home.exists() else 'java'
raise SystemExit(subprocess.call([java,'-jar',str(root/'flowpilot/app/target/app-1.0.0.jar')],env=env,cwd=root))
