#!/usr/bin/env python3
from pathlib import Path
root=Path(__file__).resolve().parents[1]
out=root/'docs/project-layout.md'
out.touch(exist_ok=True)
excluded={'.git','.agents','node_modules','target','dist','.env','.DS_Store'}
lines=['# 当前实际项目目录','', '由 scripts/generate-layout.py 生成；排除依赖、构建产物、私密环境和本地测试记录。','', '```text','FlowPilot/']
def tree(directory,prefix=''):
    entries=sorted((p for p in directory.iterdir() if p.name not in excluded and not p.name.endswith('.tsbuildinfo')),key=lambda p:(not p.is_dir(),p.name))
    for i,p in enumerate(entries):
        last=i==len(entries)-1
        lines.append(prefix+('└── ' if last else '├── ')+p.name+('/' if p.is_dir() else ''))
        if p.is_dir():tree(p,prefix+('    ' if last else '│   '))
tree(root)
lines.extend(['```',''])
out.write_text('\n'.join(lines))
print('Updated docs/project-layout.md')
