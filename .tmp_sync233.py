# -*- coding: utf-8 -*-
import io
p = r'C:\Users\57064\app\TVRemoteIME\IMEService\build.gradle'
s = io.open(p, encoding='utf-8').read()
s = s.replace(': 20302', ': 20303').replace('"2.3.2"', '"2.3.3"')
io.open(p, 'w', encoding='utf-8').write(s)
for p in [r'C:\Users\57064\app\TVRemoteIME\IMEService\src\main\res\raw\index.html',
          r'C:\Users\57064\app\TVRemoteIME\CLAUDE.md']:
    s = io.open(p, encoding='utf-8').read()
    s = s.replace('2.3.2', '2.3.3').replace('20302', '20303')
    io.open(p, 'w', encoding='utf-8').write(s)
p = r'C:\Users\57064\app\TVRemoteIME\readme.md'
s = io.open(p, encoding='utf-8').read()
entry = '### 2026-10 更新（v2.3.3）\n\n- QQ 交流群：1109483648 固定显示在屏幕底部（不再随内容滚动），居中、小号字\n\n'
if 'v2.3.3' not in s:
    s = s.replace('## 更新日志（Fork 版本）\n\n', '## 更新日志（Fork 版本）\n\n' + entry, 1)
s = s.replace('IMEService-2.3.2.apk', 'IMEService-2.3.3.apk').replace('download/2.3.2', 'download/2.3.3')
io.open(p, 'w', encoding='utf-8').write(s)
print('2.3.3 synced OK')
