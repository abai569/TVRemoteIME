package com.android.tvremoteime;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.android.tvremoteime.http.HTTPGet;

import org.json.JSONArray;
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
    /** 检查/下载进度弹窗（TV 端手动检查时显示，网页端 checkSync 不创建） */
    private Dialog progressDialog = null;

    /** GitHub 最新 Release API（直连地址，代理会自动拼接前缀）；在线更新直接拉最新 Release，不再依赖 version.json */
    private static String GITHUB_API_URL = "https://api.github.com/repos/abai569/TVRemoteIME/releases/latest";
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
            JSONObject release = getLatestRelease(errors);
            if(release == null){
                return "获取版本信息失败，已依次尝试以下地址：\r\n" + join(errors);
            }
            String tagName = release.optString("tag_name", "");
            if(!needUpdate(tagName)){
                return "当前已是最新版本：" + AppPackagesHelper.getCurrentPackageVersion(context);
            }
            if(downloadInstallAPK(release, errors)){
                return "发现新版本 " + tagName + "，已在电视端弹出更新提示";
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
                    // 点“检查更新”立即弹窗反馈，避免网络慢时看起来“没反应”
                    if(manual){
                        showProgressDialog("正在检查更新…", true);
                    }
                    List<String> errors = new ArrayList<String>();
                    JSONObject release = getLatestRelease(errors);
                    if(release == null){
                        dismissProgressDialog();
                        showDialog("更新检查失败", "获取版本信息失败，已依次尝试以下地址：\r\n" + join(errors));
                        return;
                    }
                    String tagName = release.optString("tag_name", "");
                    if(!needUpdate(tagName)){
                        dismissProgressDialog();
                        if(manual){
                            String versionName = AppPackagesHelper.getCurrentPackageVersion(context);
                            showDialog("更新检查", "当前已是最新版本：" + versionName);
                        }
                        return;
                    }
                    if(downloadInstallAPK(release, errors)){
                        dismissProgressDialog();
                        String message = release.optString("body", "");
                        StringBuilder msg = new StringBuilder();
                        msg.append("发现新版本：").append(tagName);
                        if(!TextUtils.isEmpty(message)){
                            msg.append("\r\n更新内容：\r\n\r\n").append(message);
                        }
                        final String content = msg.toString();
                        showUpdateDialog(context.getString(R.string.app_name) + "版本更新提示", content, true, true);
                    } else {
                        dismissProgressDialog();
                        showDialog("更新检查失败", "新版本下载失败，已依次尝试以下地址：\r\n" + join(errors));
                    }
                }catch (Exception e){
                    Log.e(TAG, "startUpdateThread", e);
                    dismissProgressDialog();
                    showDialog("更新检查失败", "更新检查异常：" + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }
        });
        thread.start();
    }

    /** 显示检查/下载进度弹窗（indeterminate=true 转圈表示“检查中”，false 为水平进度条+百分比） */
    private void showProgressDialog(final String title, final boolean indeterminate){
        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if(progressDialog != null){
                        progressDialog.dismiss();
                        progressDialog = null;
                    }
                    final Dialog dialog = new Dialog(context);
                    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                    dialog.setContentView(R.layout.dialog_progress);
                    dialog.setCanceledOnTouchOutside(false);
                    if(!(context instanceof Activity)){
                        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
                    }
                    ((TextView) dialog.findViewById(R.id.dlgProgressTitle)).setText(title);
                    ProgressBar pb = dialog.findViewById(R.id.dlgProgressBar);
                    TextView pct = dialog.findViewById(R.id.dlgProgressPercent);
                    pb.setIndeterminate(indeterminate);
                    if(indeterminate){
                        pct.setVisibility(View.GONE);
                    } else {
                        pct.setVisibility(View.VISIBLE);
                        pb.setProgress(0);
                        pct.setText("0%");
                    }
                    dialog.show();
                    progressDialog = dialog;
                } catch (Exception ex) {
                    Log.e(TAG, "showProgressDialog", ex);
                }
            }
        });
    }

    /** 更新下载进度（仅进度弹窗存在时生效） */
    private void updateProgress(final int pct){
        if(progressDialog == null) return;
        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if(progressDialog == null) return;
                    ProgressBar pb = progressDialog.findViewById(R.id.dlgProgressBar);
                    TextView tv = progressDialog.findViewById(R.id.dlgProgressPercent);
                    pb.setProgress(pct);
                    tv.setText(pct + "%");
                } catch (Exception ex) {
                    // 弹窗已关闭等情况忽略
                }
            }
        });
    }

    /** 关闭进度弹窗 */
    private void dismissProgressDialog(){
        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if(progressDialog != null){
                        progressDialog.dismiss();
                    }
                } catch (Exception ex) {
                    // 忽略
                }
                progressDialog = null;
            }
        });
    }

    /** 纯提示弹窗（只显示“知道了”按钮，TV 遥控器可聚焦） */
    private void showDialog(final String title, final String reasons){
        showUpdateDialog(title, reasons, false, false);
    }

    /**
     * 自定义更新弹窗：标准 Button + 黄色焦点，TV 遥控器可正常选中
     * @param showLater    是否显示“稍后更新”按钮（false 时只显示“知道了”）
     * @param installOnNow 点“马上更新”是否安装（仅新版本弹窗为 true）
     */
    private void showUpdateDialog(final String title, final String message, final boolean showLater, final boolean installOnNow){
        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    final Dialog dialog = new Dialog(context);
                    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                    dialog.setContentView(R.layout.dialog_update);
                    dialog.setCanceledOnTouchOutside(false);
                    if(!(context instanceof Activity)){
                        // 非 Activity context（如 IME 服务）需要系统悬浮窗类型，TV ROM 未授权时会失败
                        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
                    }
                    ((TextView) dialog.findViewById(R.id.dlgTitle)).setText(title);
                    ((TextView) dialog.findViewById(R.id.dlgMessage)).setText(message);

                    Button btnLater = dialog.findViewById(R.id.btnDialogLater);
                    Button btnNow = dialog.findViewById(R.id.btnDialogNow);
                    Button btnOk = dialog.findViewById(R.id.btnDialogOk);

                    View firstFocus;
                    if(showLater){
                        btnLater.setVisibility(View.VISIBLE);
                        btnNow.setVisibility(View.VISIBLE);
                        btnOk.setVisibility(View.GONE);
                        btnLater.setOnClickListener(new View.OnClickListener() {
                            @Override public void onClick(View v) { dialog.dismiss(); }
                        });
                        btnNow.setOnClickListener(new View.OnClickListener() {
                            @Override public void onClick(View v) {
                                dialog.dismiss();
                                AppPackagesHelper.installPackage(localFile, context);
                            }
                        });
                        firstFocus = btnNow;
                    } else {
                        btnLater.setVisibility(View.GONE);
                        btnNow.setVisibility(View.GONE);
                        btnOk.setVisibility(View.VISIBLE);
                        btnOk.setOnClickListener(new View.OnClickListener() {
                            @Override public void onClick(View v) { dialog.dismiss(); }
                        });
                        firstFocus = btnOk;
                    }
                    dialog.show();
                    // TV 遥控器初始焦点落到按钮上，方向键可切换
                    if(firstFocus != null){
                        firstFocus.requestFocus();
                    }
                } catch (Exception ex) {
                    Log.e(TAG, "showUpdateDialog", ex);
                    if(installOnNow){
                        AppPackagesHelper.installPackage(localFile, context);
                    }
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

    /** 获取 GitHub 最新 Release 信息：直连优先（api.github.com 国内多数网络可直接访问且更快），失败再依次走 3 个代理，记录每个地址的失败原因 */
    private JSONObject getLatestRelease(List<String> errors){
        // 直连（短超时，快速失败）
        String[] res = HTTPGet.readStringWithError(GITHUB_API_URL, 5000, 10000);
        if(res[0] != null){
            try {
                JSONObject obj = new JSONObject(res[0]);
                if(!TextUtils.isEmpty(obj.optString("tag_name", ""))){
                    return obj;
                }
                errors.add(GITHUB_API_URL + " → Release信息缺少 tag_name");
            } catch (JSONException e) {
                errors.add(GITHUB_API_URL + " → JSON解析失败: " + e.getMessage());
            }
        } else {
            errors.add(GITHUB_API_URL + " → " + res[1]);
        }
        // 代理兜底
        for(String proxy : PROXIES){
            String url = proxy + "/" + GITHUB_API_URL;
            String[] r = HTTPGet.readStringWithError(url, 5000, 10000);
            if(r[0] != null){
                try {
                    JSONObject obj = new JSONObject(r[0]);
                    if(!TextUtils.isEmpty(obj.optString("tag_name", ""))){
                        return obj;
                    }
                    errors.add(url + " → Release信息缺少 tag_name");
                } catch (JSONException e) {
                    errors.add(url + " → JSON解析失败: " + e.getMessage());
                }
            } else {
                errors.add(url + " → " + r[1]);
            }
        }
        return null;
    }

    /** 从 Release 信息中取 APK 下载地址（assets 里的 IMEService-*.apk） */
    private String getApkUrl(JSONObject release){
        try {
            JSONArray assets = release.optJSONArray("assets");
            if(assets != null){
                for(int i = 0; i < assets.length(); i++){
                    JSONObject asset = assets.getJSONObject(i);
                    String name = asset.optString("name", "");
                    if(name.startsWith("IMEService-") && name.endsWith(".apk")){
                        return asset.optString("browser_download_url", "");
                    }
                }
            }
            // 兜底：GitHub 直链 latest/download/IMEService-<tag>.apk
            String tag = release.optString("tag_name", "");
            if(!TextUtils.isEmpty(tag)){
                return "https://github.com/abai569/TVRemoteIME/releases/latest/download/IMEService-" + tag + ".apk";
            }
        }catch (JSONException e){
            Log.e(TAG, "getApkUrl", e);
        }
        return "";
    }

    /** 语义化版本比较：remote（如 2.2.2）是否比 local（如 2.1.9）新 */
    private static boolean isNewerVersion(String remote, String local){
        int[] r = parseVersion(remote);
        int[] l = parseVersion(local);
        if(r == null || l == null) return false;
        for(int i = 0; i < 3; i++){
            if(r[i] > l[i]) return true;
            if(r[i] < l[i]) return false;
        }
        return false;
    }

    private static int[] parseVersion(String v){
        try {
            if(TextUtils.isEmpty(v)) return null;
            String[] parts = v.trim().replaceFirst("^[vV]", "").split("\\.");
            int[] nums = new int[3];
            for(int i = 0; i < 3; i++){
                nums[i] = i < parts.length ? Integer.parseInt(parts[i].trim()) : 0;
            }
            return nums;
        }catch (Exception e){
            return null;
        }
    }

    private boolean needUpdate(String remoteTag){
        if(TextUtils.isEmpty(remoteTag)) return false;
        String localVersionName = AppPackagesHelper.getCurrentPackageVersion(context);
        return isNewerVersion(remoteTag, localVersionName);
    }

    private boolean needDownloadAPK(String targetVersionName) {
        try {
            if(!this.localFile.exists()) return true;

            PackageManager pm = context.getPackageManager();
            PackageInfo packInfo = pm.getPackageArchiveInfo(this.localFile.getAbsolutePath(), PackageManager.GET_ACTIVITIES);
            String localVersion = packInfo.versionName;
            if(Environment.needDebug) Environment.debug(TAG, "needDownloadAPK: localVersion = " + localVersion);
            return !targetVersionName.equals(localVersion);
        }catch (Exception e){
            return true;
        }
    }

    /** 校验本地 APK 是否可解析（下载完整性检查：半截/损坏文件无法解析包信息） */
    private boolean isValidApk(File f){
        try {
            if(f == null || !f.exists() || f.length() < 1000) return false;
            PackageManager pm = context.getPackageManager();
            PackageInfo packInfo = pm.getPackageArchiveInfo(f.getAbsolutePath(), PackageManager.GET_ACTIVITIES);
            return packInfo != null && !TextUtils.isEmpty(packInfo.packageName);
        }catch (Exception e){
            return false;
        }
    }

    /** 依次尝试 3 个代理 + 直连 下载 Release 的 APK（带下载进度回调），记录每个地址的失败原因 */
    private boolean downloadInstallAPK(JSONObject release, List<String> errors){
        try {
            String tagName = release.optString("tag_name", "");
            if(!needDownloadAPK(tagName)){
                return true;
            }

            String url = getApkUrl(release);
            if(TextUtils.isEmpty(url)) {
                errors.add("Release 中找不到 APK 下载地址");
                return false;
            }
            // 下载阶段把“检查中”弹窗切换为水平进度条（网页端 checkSync 无弹窗，跳过）
            if(progressDialog != null){
                showProgressDialog("正在下载更新…", false);
            }
            if(Environment.needDebug) Environment.debug(TAG, "downloadInstallAPK starting: " + url);
            // 进度回调（按百分比去重，避免 UI 频繁刷新）
            final int[] lastPct = { -1 };
            HTTPGet.ProgressListener listener = new HTTPGet.ProgressListener() {
                @Override
                public void onProgress(long downloaded, long total) {
                    int pct = total > 0 ? (int)(downloaded * 100 / total) : -1;
                    if(pct >= 0 && pct != lastPct[0]){
                        lastPct[0] = pct;
                        updateProgress(pct);
                    }
                }
            };
            for(String proxy : PROXIES){
                String proxyUrl = proxy + "/" + url;
                String[] res = HTTPGet.downloadFileWithError(proxyUrl, this.localFile, listener, 5000, 10000);
                if("true".equals(res[0])){
                    // 下载完成必须校验 APK 可解析：代理传输中断会得到半截文件，直接安装会报“未签名/解析失败”
                    if(isValidApk(this.localFile)){
                        if(Environment.needDebug) Environment.debug(TAG, "downloadInstallAPK finished via " + proxyUrl);
                        return true;
                    }
                    errors.add(proxyUrl + " → 下载不完整，APK无法解析，尝试下一个源");
                    continue;
                }
                errors.add(proxyUrl + " → " + res[1]);
            }
            String[] res = HTTPGet.downloadFileWithError(url, this.localFile, listener, 5000, 10000);
            if("true".equals(res[0]) && isValidApk(this.localFile)){
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
