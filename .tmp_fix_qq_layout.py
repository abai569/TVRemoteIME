# -*- coding: utf-8 -*-
import io
p = r'C:\Users\57064\app\TVRemoteIME\IMEService\src\main\res\layout\activity_main.xml'
s = io.open(p, encoding='utf-8').read()

# 1) 根 ScrollView -> 外层 LinearLayout + ScrollView(weight=1)
old_head = '''<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/md_theme_background"
    android:fillViewport="true"
    android:focusable="false"
    android:focusableInTouchMode="false"
    tools:context="com.android.tvremoteime.MainActivity">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="12dp">'''
new_head = '''<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="@color/md_theme_background">

    <ScrollView
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:fillViewport="true"
        android:focusable="false"
        android:focusableInTouchMode="false"
        tools:context="com.android.tvremoteime.MainActivity">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:padding="12dp">'''
assert old_head in s, "head pattern not found"
s = s.replace(old_head, new_head, 1)

# 2) 移除 ScrollView 内 QQ 群
old_qq = '''
            <!-- QQ 交流群（底部居中，小号字） -->
            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="6dp"
                android:gravity="center"
                android:text="QQ交流群：1109483648"
                android:textColor="#80606060"
                android:textSize="12sp" />

'''
assert old_qq in s, "qq block not found"
s = s.replace(old_qq, '\n', 1)

# 3) 尾部：闭合 ScrollView 后加固定底部 QQ 群 + 外层闭合
old_tail = '''
        </LinearLayout>

    </LinearLayout>

</ScrollView>'''
new_tail = '''
        </LinearLayout>

    </ScrollView>

    <!-- QQ 交流群（固定屏幕底部，居中，小号字） -->
    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="4dp"
        android:paddingBottom="8dp"
        android:gravity="center"
        android:text="QQ交流群：1109483648"
        android:textColor="#80606060"
        android:textSize="12sp" />

</LinearLayout>'''
assert old_tail in s, "tail pattern not found"
s = s.replace(old_tail, new_tail, 1)

io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('layout restructured OK')

# XML 校验
import xml.etree.ElementTree as ET
ET.parse(p)
print('XML OK')
