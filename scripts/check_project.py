#!/usr/bin/env python3
"""Validate tracked source structure, module DAG and delivery contracts."""
from pathlib import Path
import xml.etree.ElementTree as ET
import re
root=Path(__file__).resolve().parents[1]
ns={'m':'http://maven.apache.org/POM/4.0.0'}
parent=ET.parse(root/'flowpilot/pom.xml')
modules=[e.text for e in parent.findall('m:modules/m:module',ns)]
graph={}
for module in modules:
    pom=ET.parse(root/f'flowpilot/{module}/pom.xml')
    graph[module]=[e.find('m:artifactId',ns).text for e in pom.findall('m:dependencies/m:dependency',ns) if e.find('m:groupId',ns).text=='com.flowpilot']
    for dep in graph[module]:assert dep in modules,dep

def visit(node,path):
    assert node not in path,f'Module cycle: {path+[node]}'
    for dep in graph[node]:visit(dep,path+[node])
for module in graph:visit(module,[])
assert len(modules)==11
schema=(root/'docs/database/schema.sql').read_text()
assert len(re.findall(r'CREATE TABLE ',schema))==36
assert len(list((root/'docs/diagrams').glob('*.mmd')))==8
for p in (root/'flowpilot').rglob('*Controller.java'):
    assert '.mapper.' not in p.read_text(),f'Controller imports mapper: {p}'
for p in (root/'deploy').rglob('*.sh'):assert p.read_text().startswith('#!/usr/bin/env bash')
assert (root/'flowpilot-web/package-lock.json').is_file()
print(f'PASS: {len(modules)} modules, acyclic dependencies, 36 tables, 8 diagrams, controllers use services, lockfile present')
