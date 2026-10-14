package com.android.tvremoteime.server;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import com.android.tvremoteime.Environment;
import com.android.tvremoteime.IMEService;

import org.json.JSONObject;

import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * 电视剪贴板回传（电视 → 手机）。
 *
 * Android 10（API 29）起，系统只允许三类调用方读取剪贴板：
 *   1) 当前默认输入法；2) 当前持有焦点的应用；3) 系统 / shell。
 * 本应用靠“输入法身份”读取，所以处于 ADB 模式（未设为默认输入法）时会被系统拒绝；
 * 这种情况必须单独返回 not_default_ime，否则用户只会看到“剪贴板为空”，无从排查。
 *
 * 不做任何长度截断：剪贴板内容本身受 Binder 事务大小（约 1MB）天然限制，
 * 再叠一个人为上限只会造成“复制了一半”。
 */
public class ClipboardRequestProcesser implements RequestProcesser {
    private static final String TAG = "ClipboardRequest";

    private Context context;

    public ClipboardRequestProcesser(Context context) {
        this.context = context;
    }

    @Override
    public boolean isRequest(NanoHTTPD.IHTTPSession session, String fileName) {
        return session.getMethod() == NanoHTTPD.Method.GET && "/clipboard".equals(fileName);
    }

    @Override
    public NanoHTTPD.Response doResponse(NanoHTTPD.IHTTPSession session, String fileName,
                                         Map<String, String> params, Map<String, String> files) {
        // API 29 以下系统没有剪贴板读取限制，不强制要求默认输入法身份
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !Environment.isDefaultIME(context)) {
            return statusResponse("not_default_ime");
        }

        try {
            ClipboardManager clipboardManager =
                    (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboardManager == null || !clipboardManager.hasPrimaryClip()) {
                return statusResponse("empty");
            }
            ClipData clip = clipboardManager.getPrimaryClip();
            if (clip == null || clip.getItemCount() <= 0) {
                return statusResponse("empty");
            }
            // coerceToText 兼容 Uri / Intent 等非纯文本的剪贴内容，getText 在这些情况下为 null
            CharSequence text = clip.getItemAt(0).coerceToText(context);
            if (TextUtils.isEmpty(text)) {
                return statusResponse("empty");
            }

            String content = text.toString();
            if (Environment.needDebug) {
                Environment.debug(IMEService.TAG, "读取电视剪贴板成功，长度=" + content.length());
            }
            JSONObject result = new JSONObject();
            result.put("status", "ok");
            result.put("text", content);
            result.put("len", content.length());
            return RemoteServer.createJSONResponse(NanoHTTPD.Response.Status.OK, result.toString());
        } catch (Throwable t) {
            Log.e(IMEService.TAG, "读取电视剪贴板失败", t);
            return statusResponse("error");
        }
    }

    private NanoHTTPD.Response statusResponse(String status) {
        try {
            JSONObject result = new JSONObject();
            result.put("status", status);
            return RemoteServer.createJSONResponse(NanoHTTPD.Response.Status.OK, result.toString());
        } catch (Throwable t) {
            return RemoteServer.createPlainTextResponse(NanoHTTPD.Response.Status.OK, "{\"status\":\"error\"}");
        }
    }
}
