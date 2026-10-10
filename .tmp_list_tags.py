# -*- coding: utf-8 -*-
import io, re
p = r'C:\Users\57064\app\TVRemoteIME\IMEService\src\main\res\layout\activity_main.xml'
lines = io.open(p, encoding='utf-8').read().split('\n')
for i, line in enumerate(lines, 1):
    t = line.strip()
    if re.match(r'<LinearLayout(?:\s|>)', t) or t == '</LinearLayout>' or re.match(r'<(/?)(ScrollView|TextView|EditText|ImageView)(?:\s|>|/)', t) and ('QQ' in t or 'ScrollView' in t):
        print(f'{i:4} {line}')
