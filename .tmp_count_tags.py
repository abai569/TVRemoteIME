# -*- coding: utf-8 -*-
import io, re
p = r'C:\Users\57064\app\TVRemoteIME\IMEService\src\main\res\layout\activity_main.xml'
s = io.open(p, encoding='utf-8').read()

# 数所有开/闭标签
opens = re.findall(r'<([a-zA-Z][\w.-]*)(?:\s|>)', s)
closes = re.findall(r'</([a-zA-Z][\w.-]*)>', s)
from collections import Counter
co, cc = Counter(opens), Counter(closes)
print('未闭合标签差（开-闭）:')
for tag in sorted(set(co) | set(cc)):
    d = co[tag] - cc[tag]
    if d != 0:
        print(f'  {tag}: open={co[tag]} close={cc[tag]} diff={d}')

# 逐行找错配
stack = []
lines = s.split('\n')
for i, line in enumerate(lines, 1):
    # 去掉注释行
    if '<!--' in line and '-->' not in line:
        # 多行注释开始
        pass
    # 单行内的标签（忽略自闭合和注释）
    line_clean = re.sub(r'<!--.*?-->', '', line)
    for m in re.finditer(r'<(/?)([a-zA-Z][\w.-]*)([^>]*?)(/?)>', line_clean):
        is_close, tag, attrs, self_close = m.group(1), m.group(2), m.group(3), m.group(4)
        if self_close:
            continue
        if is_close:
            if not stack or stack[-1] != tag:
                print(f'line {i}: </{tag}> 不匹配栈顶 {stack[-1] if stack else None} (content: {line.strip()[:60]})')
                stack = []
            else:
                stack.pop()
        else:
            stack.append(tag)
if stack:
    print('结束时未闭合:', stack)
else:
    print('标签全部配对')
