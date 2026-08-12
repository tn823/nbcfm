package com.example.nbcfm;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.File;

public class AppUpdater {

    public static void checkForUpdate(final Activity activity) {
        // Xóa file APK tạm của phiên bản trước (nếu có) để giải phóng bộ nhớ cho tablet
        try {
            File tempApk = new File(
                activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                "nbcfm.apk"
            );
            if (tempApk.exists()) {
                tempApk.delete();
            }
        } catch (Exception ignored) {}

        new AsyncTask<Void, Void, JSONObject>() {
            @Override
            protected JSONObject doInBackground(Void... v) {
                try {
                    HttpHandler sh = new HttpHandler();
                    String json = sh.makeServiceCall(Config.CHECK_UPDATE);
                    if (json == null) return null;
                    return new JSONObject(json.trim());
                } catch (Exception e) {
                    Log.e("AppUpdater", "Lỗi tải JSON cập nhật: " + e.toString());
                    return null;
                }
            }

            @Override
            protected void onPostExecute(JSONObject info) {
                if (info == null || activity.isFinishing()) return;
                try {
                    int serverVersionCode = info.getInt("version_code");
                    String serverVersionName = info.getString("version_name");
                    String apkUrl = info.getString("apk_url");
                    String changelog = info.optString("changelog", "");

                    int currentVersionCode = 1;
                    try {
                        PackageInfo pInfo = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
                        currentVersionCode = pInfo.versionCode;
                    } catch (PackageManager.NameNotFoundException ignored) {}

                    if (serverVersionCode > currentVersionCode) {
                        showUpdateDialog(activity, serverVersionName, apkUrl, changelog);
                    }
                } catch (Exception e) {
                    Log.e("AppUpdater", "Lỗi phân tích cú pháp JSON: " + e.toString());
                }
            }
        }.execute();
    }

    private static void showUpdateDialog(final Activity activity, final String version,
                                          final String apkUrl, final String changelog) {
        String message = "Phiên bản: " + version + "\n\nNội dung mới:\n" + changelog;
        new AlertDialog.Builder(activity)
            .setTitle("🆕 Có bản cập nhật mới!")
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("Cập nhật ngay", new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialog, int which) {
                    downloadAndInstall(activity, apkUrl);
                }
            })
            .setNegativeButton("Bỏ qua", null)
            .show();
    }

    private static void downloadAndInstall(final Activity activity, final String apkUrl) {
        Toast.makeText(activity, "Đang tải bản cập nhật...", Toast.LENGTH_LONG).show();

        // Lưu vào thư mục Download riêng của app để FileProvider truy cập dễ dàng
        final File outputFile = new File(
            activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "nbcfm.apk"
        );
        if (outputFile.exists()) {
            outputFile.delete();
        }

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle("Tải bản cập nhật nbcfm")
            .setDescription("Vui lòng đợi trong giây lát...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE) // Chỉ hiện khi đang tải, tự ẩn khi xong
            .setDestinationUri(Uri.fromFile(outputFile));

        final DownloadManager dm = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm == null) return;
        final long downloadId = dm.enqueue(request);

        BroadcastReceiver onComplete = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id == downloadId) {
                    try {
                        activity.unregisterReceiver(this);
                    } catch (Exception ignored) {}
                    // KHÔNG dùng dm.remove(downloadId) ở đây vì lệnh đó sẽ XÓA FILE APK vừa tải về!
                    installApk(activity, outputFile);
                }
            }
        };

        activity.registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    private static void installApk(Activity activity, File file) {
        if (file == null || !file.exists()) {
            Toast.makeText(activity, "Lỗi: Không tìm thấy file APK đã tải về.", Toast.LENGTH_LONG).show();
            return;
        }

        // Kiểm tra quyền cài ứng dụng từ nguồn không xác định trên Android 8.0+ (API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.getPackageManager().canRequestPackageInstalls()) {
                Toast.makeText(activity, "Vui lòng cấp quyền 'Cài đặt ứng dụng không rõ nguồn gốc' để hoàn tất cập nhật.", Toast.LENGTH_LONG).show();
                Intent permIntent = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(permIntent);
                return;
            }
        }

        Uri apkUri = FileProvider.getUriForFile(
            activity,
            activity.getPackageName() + ".fileprovider",
            file
        );

        Intent install = new Intent(Intent.ACTION_VIEW);
        install.setDataAndType(apkUri, "application/vnd.android.package-archive");
        install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            activity.startActivity(install);
        } catch (Exception e) {
            Log.e("AppUpdater", "Lỗi khởi chạy cài đặt: " + e.toString());
            Toast.makeText(activity, "Không thể mở màn hình cài đặt: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
