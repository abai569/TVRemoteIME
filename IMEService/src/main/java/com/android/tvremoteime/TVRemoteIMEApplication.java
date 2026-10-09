package com.android.tvremoteime;

import android.app.Application;

/**
 * 全局 Application：进程启动即安装崩溃日志处理器。
 * MainActivity 的 CrashHandler 只覆盖 Activity 崩溃，输入法服务/DLNA 等
 * 后台组件崩溃必须在这里全局兜底，否则覆盖安装后的服务崩溃会静默循环。
 */
public class TVRemoteIMEApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        CrashHandler.install(this);
    }
}
