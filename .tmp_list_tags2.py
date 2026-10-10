# -*- coding: utf-8 -*-
import io, re
p = r'C:\Users\57064\app\TVRemoteIME\IMEService\src\main\res\layout\activity_main.xml'
lines = io.open(p, encoding='utf-8').read().split('\n')
for i, line in enumerate(lines, 1):
    if re.search(r'<(/?)(LinearLayout|ScrollView)\b', line):
        t = line.strip()
        tag = re.search(r'<(/?)(LinearLayout|ScrollView)\b', t).group(0)
        indent = len(line) - len(line.lstrip())
        print(f'{i:4} ind={indent:2} {tag}')
