package com.android.tvremoteime;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.util.Log;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.tvremoteime.server.RemoteServer;
import com.android.tvremoteime.adb.AdbHelper;
import com.android.tvremoteime.mouse.MouseAccessibilityService;
import com.zxt.dlna.dmr.ZxtMediaRenderer;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.List;

public class MainActivity extends Activity implements View.OnClickListener {

    private static final String TAG = "MainActivity";

    private ImageView qrCodeImage;
    private TextView addressView;
    private EditText dlnaNameText;
    private TextView accessibilityStatusView;
    private Button accessibilityButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 崩溃日志：任何未捕获异常先落盘，下次启动弹窗显示，杜绝闪退无从排查
        CrashHandler.install(getApplicationContext());
        showCrashReportIfExists();

        try {
            setContentView(R.layout.activity_main);
        } catch (Throwable t) {
            // 主界面布局加载失败：记录日志并显示兜底页，绝不反复闪退
            Log.e("MainActivity", "inflate activity_main failed", t);
            CrashHandler.saveCrash(this, t);
            setContentView(R.layout.activity_fallback);
            android.widget.TextView info = findViewById(R.id.tvFallbackInfo);
            if (info != null) {
                info.setText("主界面加载失败：" + t.getClass().getSimpleName() + "\n" + t.getMessage());
            }
            return;
        }

        qrCodeImage = this.findViewById(R.id.ivQRCode);
        addressView = this.findViewById(R.id.tvAddress);
        dlnaNameText = this.findViewById(R.id.etDLNAName);
        accessibilityStatusView = this.findViewById(R.id.tvAccessibilityStatus);
        accessibilityButton = this.findViewById(R.id.btnAccessibility);

        this.setTitle(this.getResources().getString( R.string.app_name) + "  V" + AppPackagesHelper.getCurrentPackageVersion(this));
        dlnaNameText.setText(DLNAUtils.getDLNANameSuffix(this.getApplicationContext()));

        // 显示当前版本号
        TextView versionView = findViewById(R.id.tvAppVersion);
        if (versionView != null) {
            versionView.setText("当前版本：" + AppPackagesHelper.getCurrentPackageVersion(this));
        }

        // 设置按钮点击监听器
        findViewById(R.id.btnUseIME).setOnClickListener(this);
        findViewById(R.id.btnSetIME).setOnClickListener(this);
        findViewById(R.id.btnOneClickIme).setOnClickListener(this);
        findViewById(R.id.btnStartService).setOnClickListener(this);
        findViewById(R.id.btnSetDLNA).setOnClickListener(this);
        findViewById(R.id.btnCheckUpdate).setOnClickListener(this);
        if (accessibilityButton != null) {
            accessibilityButton.setOnClickListener(this);
        }

        refreshQRCode();
        updateAccessibilityStatus();

        // TV 遥控器焦点默认落在“检查更新”按钮上（header 是第一个可聚焦元素）
        View checkUpdateBtn = findViewById(R.id.btnCheckUpdate);
        if (checkUpdateBtn != null) {
            checkUpdateBtn.requestFocus();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAccessibilityStatus();
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btnUseIME) {
            openInputMethodSettings();
            if(Environment.isEnableIME(this)){
                Environment.toast(getApplicationContext(), "太棒了，您已经激活启用了" + getString(R.string.keyboard_name) +"输入法！");
            }
        } else if (id == R.id.btnSetIME) {
            if(!Environment.isEnableIME(this)) {
                Environment.toast(getApplicationContext(), "抱歉，请您先激活启用" + getString(R.string.keyboard_name) +"输入法！");
                openInputMethodSettings();
                if(!Environment.isEnableIME(this)) return;
            }
            try {
                ((InputMethodManager) getApplicationContext().getSystemService(Context.INPUT_METHOD_SERVICE)).showInputMethodPicker();
            }catch (Exception ignored) {
                Environment.toast(getApplicationContext(), "抱歉，无法设置为系统默认输入法，请手动启动服务！");
            }
            if(Environment.isDefaultIME(this)){
                Environment.toast(getApplicationContext(), "太棒了，" + getString(R.string.keyboard_name) +"已是系统默认输入法！");
            }
        } else if (id == R.id.btnOneClickIme) {
            oneClickEnableIme();
        } else if (id == R.id.btnStartService) {
            // 使用显式Intent启动服务 (Android 5.0+要求)
            startService(new Intent(this, IMEService.class));
            if(!Environment.isDefaultIME(this)) {
                if (AdbHelper.getInstance() == null) AdbHelper.createInstance();
            }
            Environment.toast(getApplicationContext(), "服务已手动启动，稍后可尝试访问控制端页面");
        } else if (id == R.id.btnSetDLNA) {
            DLNAUtils.setDLNANameSuffix(this.getApplicationContext(), dlnaNameText.getText().toString());
        } else if (id == R.id.btnAccessibility) {
            openAccessibilitySettings();
        } else if (id == R.id.btnCheckUpdate) {
            // TV 端“检查更新”按钮：手动触发在线更新检查，结果以弹窗显示
            AutoUpdateManager.checkUpdateNow(this, new Handler(Looper.getMainLooper()));
        }
        refreshQRCode();
        updateAccessibilityStatus();
    }

    /** 上次启动若崩溃过，弹窗显示崩溃日志（TV 屏幕上直接可见，方便截图反馈），显示后清空 */
    private void showCrashReportIfExists(){
        try {
            String log = CrashHandler.readCrashLog(this);
            if(!log.isEmpty()){
                CrashHandler.clearCrashLog(this);
                new android.app.AlertDialog.Builder(this)
                        .setTitle("上次启动崩溃")
                        .setMessage(log)
                        .setPositiveButton("知道了", null)
                        .setCancelable(true)
                        .show();
            }
        } catch (Throwable ignored) {
        }
    }

    private void openInputMethodSettings(){
        try {
            this.startActivityForResult(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS), 0);
            return;
        }catch (Exception ignored){ }
        // TCL 等定制系统未注册“输入法设置”页面：能打开系统设置主页就打开（辅助），提示语统一指引手动操作
        try {
            Intent settings = new Intent(Settings.ACTION_SETTINGS);
            settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            this.startActivity(settings);
        }catch (Exception ignored2){ }
        Environment.toast(getApplicationContext(), "抱歉，无法激活输入法，请手动前往 设置→输入法，选择 " + getString(R.string.app_name));
    }

    /** 一键启用并设为默认：需 WRITE_SECURE_SETTINGS（ADB 授权一次）；未授权时弹引导 */
    private void oneClickEnableIme(){
        if(getPackageManager().checkPermission("android.permission.WRITE_SECURE_SETTINGS", getPackageName()) != PackageManager.PERMISSION_GRANTED){
            showAdbGrantDialog();
            return;
        }
        boolean ok = enableAndSetDefaultIme();
        if(ok){
            Environment.toast(getApplicationContext(), "已启用并设为默认输入法：" + getString(R.string.keyboard_name));
        }else{
            Environment.toast(getApplicationContext(), "设置失败，请手动前往 设置→输入法 选择 " + getString(R.string.app_name));
        }
    }

    /** 写入 ENABLED_INPUT_METHODS + DEFAULT_INPUT_METHOD，并回读验证 */
    private boolean enableAndSetDefaultIme(){
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            List<InputMethodInfo> imes = imm.getInputMethodList();
            String imeId = null;
            for(InputMethodInfo info : imes){
                if(info.getPackageName().equals(getPackageName())){
                    imeId = info.getId();
                    break;
                }
            }
            if(imeId == null) return false;
            ContentResolver cr = getContentResolver();
            String enabled = Settings.Secure.getString(cr, Settings.Secure.ENABLED_INPUT_METHODS);
            if(enabled == null) enabled = "";
            if(!enabled.contains(imeId)){
                enabled = enabled.trim().isEmpty() ? imeId : enabled + ":" + imeId;
                Settings.Secure.putString(cr, Settings.Secure.ENABLED_INPUT_METHODS, enabled);
            }
            Settings.Secure.putString(cr, Settings.Secure.DEFAULT_INPUT_METHOD, imeId);
            return imeId.equals(Settings.Secure.getString(cr, Settings.Secure.DEFAULT_INPUT_METHOD));
        } catch (Exception e){
            Log.e(TAG, "enableAndSetDefaultIme", e);
            return false;
        }
    }

    /** 未授权 WRITE_SECURE_SETTINGS 时的 ADB 授权引导（显示电视 IP + 电脑端命令） */
    private void showAdbGrantDialog(){
        try {
            String ip = getLocalIpAddress();
            String cmd = "adb connect " + ip + ":5555\r\nadb shell pm grant " + getPackageName() + " android.permission.WRITE_SECURE_SETTINGS";
            new android.app.AlertDialog.Builder(this)
                    .setTitle("需要一次 ADB 授权")
                    .setMessage("电视已开启 ADB 时，在电脑上执行：\r\n\r\n" + cmd + "\r\n\r\n授权完成后，再点一次“一键启用并设为默认”，即可直接启用并设为默认输入法，无需进系统设置。")
                    .setPositiveButton("知道了", null)
                    .setCancelable(true)
                    .show();
        } catch (Exception e){
            Log.e(TAG, "showAdbGrantDialog", e);
        }
    }

    /** 获取本机（电视）局域网 IPv4，用于 adb connect */
    private String getLocalIpAddress(){
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while(nis != null && nis.hasMoreElements()){
                NetworkInterface ni = nis.nextElement();
                if(ni.isLoopback() || !ni.isUp()) continue;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while(addrs.hasMoreElements()){
                    InetAddress addr = addrs.nextElement();
                    String host = addr.getHostAddress();
                    if(!addr.isLoopbackAddress() && host != null && host.contains(".")){
                        return host;
                    }
                }
            }
        } catch (Exception e){
            Log.e(TAG, "getLocalIpAddress", e);
        }
        return "192.168.x.x";
    }

    private void openAccessibilitySettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
            Environment.toast(getApplicationContext(), "请在列表中找到\"" + getString(R.string.app_name) + "\"并启用");
        } catch (Exception e) {
            Environment.toast(getApplicationContext(), "无法打开辅助功能设置，请手动前往：设置 → 辅助功能");
        }
    }

    private void updateAccessibilityStatus() {
        boolean isEnabled = isAccessibilityServiceEnabled();
        if (accessibilityStatusView != null) {
            if (isEnabled) {
                accessibilityStatusView.setText("已启用");
                accessibilityStatusView.setTextColor(0xFF4CAF50); // 绿色
            } else {
                accessibilityStatusView.setText("未启用");
                accessibilityStatusView.setTextColor(0xFFFF5722); // 橙色
            }
        }
        if (accessibilityButton != null) {
            accessibilityButton.setText(isEnabled ? "已启用" : "设置");
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        // 首先检查服务实例是否存在
        if (MouseAccessibilityService.isServiceEnabled()) {
            return true;
        }
        // 然后检查系统设置
        String serviceName = getPackageName() + "/" + MouseAccessibilityService.class.getCanonicalName();
        try {
            String enabledServices = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            );
            if (!TextUtils.isEmpty(enabledServices)) {
                return enabledServices.contains(serviceName);
            }
        } catch (Exception e) {
            // 忽略异常
        }
        return false;
    }

    private void refreshQRCode(){
        String address = RemoteServer.getServerAddress(this);
        addressView.setText(address);
        // 生成分辨率保持 150，避免加大生成尺寸在个别电视 ROM 上的潜在兼容问题
        qrCodeImage.setImageBitmap(QRCodeGen.generateBitmap(address, 360, 360));
    }




}
