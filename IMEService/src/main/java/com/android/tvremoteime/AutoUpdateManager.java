package com.android.tvremoteime;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.WindowManager;

import com.android.tvremoteime.http.HTTPGet;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
/**
 * Created by kingt on 2018/4/11.
 */
public class AutoUpdateManager {
    private static String TAG = "AutoUpdateManager";
    private static AutoUpdateManager instance = null;
    private Context context;
    private Handler handler;
    private File localFile = null;

    /** 版本信息清单：本仓库 released/version.json（直连地址，代理会自动拼接前缀） */
    private static String VERSION_FILE_URL = "https://raw.githubusercontent.com/abai569/TVRemoteIME/master/released/version.json";
    /** GitHub 加速代理列表（按顺序尝试，全部失败后走直连） */
    private static final String[] PROXIES = {
            "https://git-proxy.abai.eu.org",
            "https://gh-proxy.com",
            "https://ghfast.top"
    };

    public AutoUpdateManager(Context context, Handler handler){
        this.context = context;
        this.handler = handler;
        this.localFile = new File(context.getExternalCacheDir(), context.getString(R.string.app_name) + ".apk");
        AutoUpdateManager.instance = this;
        this.startUpdateThread(false);
    }

    /** 手动触发一次更新检查（网页控制端“检查更新”按钮调用） */
    public static void checkUpdateNow(){
        if(AutoUpdateManager.instance != null){
            AutoUpdateManager.instance.startUpdateThread(true);
        }
    }

    /** 手动触发一次更新检查（TV 端主界面“检查更新”按钮调用；弹窗使用传入的 Activity context，保证电视上一定能弹出） */
    public static void checkUpdateNow(Context context, Handler handler){
        if(AutoUpdateManager.instance == null){
            new AutoUpdateManager(context, handler);
        } else {
            AutoUpdateManager.instance.setUiContext(context, handler);
            AutoUpdateManager.instance.startUpdateThread(true);
        }
    }

    /** 更新弹窗使用的 UI context（TV 端按钮传入 Activity，弹窗走普通窗口，不依赖悬浮窗权限） */
    public void setUiContext(Context context, Handler handler){
        if(context != null){
            this.context = context;
        }
        if(handler != null){
            this.handler = handler;
        }
    }

    /** 同步执行一次更新检查并返回结果文本（网页控制端 /checkUpdate 用，结果直接显示在网页上） */
    public static String checkUpdateAndGetResult(){
        if(AutoUpdateManager.instance == null){
            return "更新检查未初始化：请先启动小盒精灵服务";
        }
        return AutoUpdateManager.instance.checkSync();
    }

    private String checkSync(){
        try{
            List<String> errors = new ArrayList<String>();
            JSONObject versionObj = getServerVersionObj(errors);
            if(versionObj == null){
                return "获取版本信息失败，已依次尝试以下地址：\r\n" + join(errors);
            }
            if(!needUpdate(versionObj)){
                return "当前已是最新版本：" + AppPackagesHelper.getCurrentPackageVersion(context);
            }
            if(downloadInstallAPK(versionObj, errors)){
                String versionName = versionObj.has("versionName") ? versionObj.getString("versionName") : "新版本";
                return "发现新版本 " + versionName + "，已在电视端弹出更新提示";
            } else {
                return "新版本下载失败，已依次尝试以下地址：\r\n" + join(errors);
            }
        }catch (Exception e){
            Log.e(TAG, "checkSync", e);
            return "更新检查异常：" + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    private void startUpdateThread(final boolean manual){
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try{
                    List<String> errors = new ArrayList<String>();
                    JSONObject versionObj = getServerVersionObj(errors);
                    if(versionObj == null){
                        showDialog("更新检查失败", "获取版本信息失败，已依次尝试以下地址：\r\n" + join(errors));
                        return;
                    }
                    if(!needUpdate(versionObj)){
                        if(manual){
                            String versionName = AppPackagesHelper.getCurrentPackageVersion(context);
                            showDialog("更新检查", "当前已是最新版本：" + versionName);
                        }
                        return;
                    }
                    if(downloadInstallAPK(versionObj, errors)){
                        String message = versionObj.has("message") ? versionObj.getString("message") : "";
                        String versionName = versionObj.has("versionName") ? versionObj.getString("versionName") : AppPackagesHelper.getCurrentPackageVersion(context);
                        StringBuilder msg = new StringBuilder();
                        msg.append("发现新版本：").append(versionName);
                        if(!TextUtils.isEmpty(message)){
                            msg.append("\r\n更新内容：\r\n\r\n").append(message);
                        }
                        final String content = msg.toString();
                        final boolean forced = versionObj.has("forced") && versionObj.getBoolean("forced");

                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                                builder.setTitle(context.getString(R.string.app_name) + "版本更新提示").setIcon(R.drawable.ic_launcher);
                                builder.setMessage(content);
                                if (!forced) {
                                    builder.setPositiveButton("稍后更新", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                        }
                                    });
                                }
                                builder.setNegativeButton("马上更新", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        AppPackagesHelper.installPackage(localFile, context);
                                        dialog.dismiss();
                                    }
                                });
                                try {
                                    AlertDialog dialog = builder.create();
                                    dialog.setCanceledOnTouchOutside(false);
                                    if(!(context instanceof android.app.Activity)){
                                        // 非 Activity context（如 IME 服务）需要系统悬浮窗类型，TV ROM 未授权时会失败
                                        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
                                    }
                                    dialog.show();
                                } catch (Exception ex) {
                                    AppPackagesHelper.installPackage(localFile, context);
                                }
                            }
                        });
                    } else {
                        showDialog("更新检查失败", "新版本下载失败，已依次尝试以下地址：\r\n" + join(errors));
                    }
                }catch (Exception e){
                    Log.e(TAG, "startUpdateThread", e);
                    showDialog("更新检查失败", "更新检查异常：" + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }
        });
        thread.start();
    }

    private void showDialog(final String title, final String reasons){
        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    AlertDialog.Builder builder = new AlertDialog.Builder(context);
                    builder.setTitle(title);
                    builder.setMessage(reasons);
                    builder.setPositiveButton("知道了", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
                    AlertDialog dialog = builder.create();
                    dialog.setCanceledOnTouchOutside(false);
                    if(!(context instanceof android.app.Activity)){
                        // 非 Activity context（如 IME 服务）需要系统悬浮窗类型，TV ROM 未授权时会失败
                        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
                    }
                    dialog.show();
                } catch (Exception ex) {
                    Log.e(TAG, "showDialog", ex);
                }
            }
        });
    }

    private String join(List<String> errors){
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < errors.size(); i++){
            if(i > 0) sb.append("\r\n");
            sb.append((i + 1)).append(". ").append(errors.get(i));
        }
        return sb.toString();
    }

    private int getCurrentPackageVersion(){
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionCode;
        }catch (PackageManager.NameNotFoundException e){}
        return -1;
    }

    /** 依次尝试 3 个代理 + 直连 获取版本信息，记录每个地址的失败原因 */
    private JSONObject getServerVersionObj(List<String> errors){
        for(String proxy : PROXIES){
            String url = proxy + "/" + VERSION_FILE_URL;
            String[] res = HTTPGet.readStringWithError(url);
            if(res[0] != null){
                try {
                    return new JSONObject(res[0]);
                } catch (JSONException e) {
                    errors.add(url + " → JSON解析失败: " + e.getMessage());
                }
            } else {
                errors.add(url + " → " + res[1]);
            }
        }
        String[] res = HTTPGet.readStringWithError(VERSION_FILE_URL);
        if(res[0] != null){
            try {
                return new JSONObject(res[0]);
            } catch (JSONException e) {
                errors.add(VERSION_FILE_URL + " → JSON解析失败: " + e.getMessage());
            }
        } else {
            errors.add(VERSION_FILE_URL + " → " + res[1]);
        }
        return null;
    }

    private boolean needUpdate(JSONObject versionObj){
        int version = getCurrentPackageVersion();
        if(version == -1 || versionObj == null) return false;

        try {
            return (versionObj.has("versionCode") &&
                     versionObj.has("installAPK") &&
                     version < versionObj.getInt("versionCode"));
        } catch (JSONException e) {
            return false;
        }
    }

    private boolean needDownloadAPK(JSONObject versionObj) {
        try {
            if(! this.localFile.exists()) return true;

            PackageManager pm = context.getPackageManager();
            PackageInfo packInfo = pm.getPackageArchiveInfo(this.localFile.getAbsolutePath(), PackageManager.GET_ACTIVITIES);
            int localVersion = packInfo.versionCode;
            if(Environment.needDebug) Environment.debug(TAG, "needDownloadAPK: localVersion = " + localVersion);
            int version = versionObj.getInt("versionCode");
            return localVersion < version;
        }catch (Exception e){
            return true;
        }
    }

    /** 依次尝试 3 个代理 + 直连 下载 APK，记录每个地址的失败原因 */
    private boolean downloadInstallAPK(JSONObject versionObj, List<String> errors){
        try {
            if(!needDownloadAPK(versionObj)){
                return true;
            }

            String url = versionObj.getString("installAPK");
            if(TextUtils.isEmpty(url)) {
                errors.add("version.json 中缺少 installAPK 下载地址");
                return false;
            }
            if(Environment.needDebug) Environment.debug(TAG, "downloadInstallAPK starting: " + url);
            for(String proxy : PROXIES){
                String proxyUrl = proxy + "/" + url;
                String[] res = HTTPGet.downloadFileWithError(proxyUrl, this.localFile);
                if("true".equals(res[0])){
                    if(Environment.needDebug) Environment.debug(TAG, "downloadInstallAPK finished via " + proxyUrl);
                    return true;
                }
                errors.add(proxyUrl + " → " + res[1]);
            }
            String[] res = HTTPGet.downloadFileWithError(url, this.localFile);
            if("true".equals(res[0])){
                if(Environment.needDebug) Environment.debug(TAG, "downloadInstallAPK finished via " + url);
                return true;
            }
            errors.add(url + " → " + res[1]);
            return false;
        } catch (Exception e) {
            Log.e(TAG, "downloadInstallAPK", e);
            errors.add("下载异常: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return false;
    }
}
