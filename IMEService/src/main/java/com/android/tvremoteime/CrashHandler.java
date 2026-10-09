package com.android.tvremoteime;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 崩溃日志记录：
 * 任何未捕获异常先写入 App 专属目录 crash.log（无需额外权限），
 * 下次启动时 MainActivity 读取并弹窗显示，避免闪退无从排查。
 */
public class CrashHandler {

    private static final String TAG = "CrashHandler";

    /** crash.log 位置：/sdcard/Android/data/<包名>/files/crash.log（TV 上可通过文件管理器查看） */
    public static File getCrashFile(Context context){
        File dir = context.getExternalFilesDir(null);
        if(dir == null){
            dir = context.getFilesDir();
        }
        return new File(dir, "crash.log");
    }

    /** 安装全局未捕获异常处理器（MainActivity.onCreate 最先调用） */
    public static void install(final Context context){
        final Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable ex) {
                saveCrash(context, ex);
                if(defaultHandler != null){
                    defaultHandler.uncaughtException(thread, ex);
                }
            }
        });
    }

    /** 把崩溃堆栈写入 crash.log（追加方式，保留最近多次崩溃） */
    public static void saveCrash(Context context, Throwable ex){
        try {
            File f = getCrashFile(context);
            FileWriter w = new FileWriter(f, true);
            PrintWriter pw = new PrintWriter(w);
            pw.println("========================================");
            pw.println("time=" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
            pw.println("thread=" + Thread.currentThread().getName());
            ex.printStackTrace(pw);
            pw.flush();
            pw.close();
            Log.e(TAG, "crash saved to " + f.getAbsolutePath());
        } catch (Throwable ignored) {
        }
    }

    /** 读取全部崩溃日志文本（截断到上限，防止超长） */
    public static String readCrashLog(Context context){
        try {
            File f = getCrashFile(context);
            if(!f.exists() || f.length() <= 0){
                return "";
            }
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            byte[] buf = new byte[(int) Math.min(f.length(), 4096)];
            int len = in.read(buf);
            in.close();
            return new String(buf, 0, len, "UTF-8");
        } catch (Throwable t){
            return "";
        }
    }

    /** 删除崩溃日志（显示后清理） */
    public static void clearCrashLog(Context context){
        try {
            File f = getCrashFile(context);
            if(f.exists()){
                f.delete();
            }
        } catch (Throwable ignored) {
        }
    }
}
