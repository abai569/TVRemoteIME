package com.android.tvremoteime.server;

import android.content.Context;
import android.text.TextUtils;

import com.android.tvremoteime.AppPackagesHelper;
import com.android.tvremoteime.VideoPlayHelper;

import java.io.File;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;
import com.android.tvremoteime.util.FileUtils;

/**
 * Created by kingt on 2018/1/7.
 */

public class UploadRequestProcesser implements RequestProcesser {
    private Context context;

    public UploadRequestProcesser(Context context){
        this.context = context;
    }

    @Override
    public boolean isRequest(NanoHTTPD.IHTTPSession session, String fileName) {
        return session.getMethod() == NanoHTTPD.Method.POST && "/upload".equalsIgnoreCase(fileName);
    }

    @Override
    public NanoHTTPD.Response doResponse(NanoHTTPD.IHTTPSession session, String fileName, Map<String, String> params, Map<String, String> files) {
        String uploadFileName  = params.get("file");
        Boolean autoInstall = "true".equalsIgnoreCase(params.get("autoInstall"));
        String localFilename = files.get("file");
        if(!TextUtils.isEmpty(uploadFileName)) {
            if (!TextUtils.isEmpty(localFilename)) {
                if(autoInstall) {
                    // 注意：localFilename 是服务端临时文件名（通常不带后缀），类型判断必须用原始文件名 uploadFileName
                    if (uploadFileName.toLowerCase().endsWith(".apk")) {
                        // 拷贝到 FileProvider 可暴露的目录后执行安装（避免 file:// URI 被系统拦截）
                        File apkFile = ensureInstallableFile(uploadFileName, localFilename);
                        AppPackagesHelper.installPackage(apkFile, this.context);
                    }
                    else if (FileUtils.isMediaFile(uploadFileName)){
                        //执行播放
                        VideoPlayHelper.playUrl(this.context, localFilename, 0, "true".equalsIgnoreCase(params.get("useSystem")));
                    }
                }
            }
        }
        if(TextUtils.isEmpty(localFilename)){
            return RemoteServer.createJSONResponse(NanoHTTPD.Response.Status.OK,  "{\"success\":false}");
        }else{
            return RemoteServer.createJSONResponse(NanoHTTPD.Response.Status.OK,  String.format("{\"success\":true, \"filePath\":\"%s\"}", localFilename.replaceAll("\\\\", "\\\\")));
        }
    }

    /** 把上传的 APK 拷贝到外部缓存目录（FileProvider 已覆盖），返回可安装文件 */
    private File ensureInstallableFile(String uploadFileName, String localFilename){
        File src = new File(localFilename);
        try {
            File destDir = new File(context.getExternalCacheDir(), "upload_apk");
            if(!destDir.exists() && !destDir.mkdirs()){
                return src;
            }
            // 用纯文件名，防止路径注入
            String safeName = FileUtils.getFileName(uploadFileName);
            if(TextUtils.isEmpty(safeName)){
                safeName = "upload_" + System.currentTimeMillis() + ".apk";
            }
            File dest = new File(destDir, safeName);
            java.io.FileInputStream fis = new java.io.FileInputStream(src);
            try {
                java.io.FileOutputStream fos = new java.io.FileOutputStream(dest);
                try {
                    byte[] buf = new byte[8192];
                    int n;
                    while((n = fis.read(buf)) > 0){
                        fos.write(buf, 0, n);
                    }
                    fos.flush();
                } finally {
                    fos.close();
                }
            } finally {
                fis.close();
            }
            return dest;
        } catch (Exception e){
            // 拷贝失败时回退使用原始临时文件（部分 ROM 下仍可能成功）
            return src;
        }
    }
}
