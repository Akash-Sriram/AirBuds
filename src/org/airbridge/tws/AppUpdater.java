package org.airbridge.tws;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class AppUpdater {
    private static final String TAG = "AppUpdater";
    private static final String GITHUB_LATEST_RELEASE_URL =
            "https://api.github.com/repos/Akash-Sriram/AirBuds/releases/latest";

    public static void checkForUpdate(Activity activity, boolean userInitiated) {
        if (activity == null || activity.isFinishing()) return;

        if (userInitiated) {
            Toast.makeText(activity, "Checking for updates...", Toast.LENGTH_SHORT).show();
        }

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(GITHUB_LATEST_RELEASE_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "AirBuds-Android-App");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                int responseCode = conn.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    if (userInitiated) {
                        showToast(activity, "Unable to check updates (HTTP " + responseCode + ")");
                    }
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                JSONObject release = new JSONObject(sb.toString());
                String tagName = release.optString("tag_name", "").trim();
                String releaseName = release.optString("name", tagName);
                String body = release.optString("body", "").trim();

                // Find APK asset download URL
                String apkDownloadUrl = null;
                long apkSize = 0;
                JSONArray assets = release.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.getJSONObject(i);
                        String assetName = asset.optString("name", "");
                        if (assetName.endsWith(".apk")) {
                            apkDownloadUrl = asset.optString("browser_download_url", null);
                            apkSize = asset.optLong("size", 0);
                            break;
                        }
                    }
                }

                PackageInfo pInfo = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
                String currentVersion = pInfo.versionName != null ? pInfo.versionName : "1.0.0";

                final String finalApkUrl = apkDownloadUrl;
                final long finalApkSize = apkSize;

                if (isNewerVersion(tagName, currentVersion) && finalApkUrl != null) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (!activity.isFinishing()) {
                            showUpdateDialog(activity, tagName, releaseName, body, finalApkUrl, finalApkSize);
                        }
                    });
                } else if (userInitiated) {
                    showToast(activity, "AirBuds is up to date (v" + currentVersion + ")");
                }

            } catch (Exception e) {
                Log.e(TAG, "Update check failed", e);
                if (userInitiated) {
                    showToast(activity, "Update check failed: " + e.getMessage());
                }
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }

    private static boolean isNewerVersion(String remoteTag, String currentVersion) {
        if (remoteTag == null || remoteTag.isEmpty()) return false;
        String cleanRemote = remoteTag.replaceAll("[^0-9.]", "").trim();
        String cleanCurrent = currentVersion.replaceAll("[^0-9.]", "").trim();

        String[] rParts = cleanRemote.split("\\.");
        String[] cParts = cleanCurrent.split("\\.");

        int length = Math.max(rParts.length, cParts.length);
        for (int i = 0; i < length; i++) {
            int rVal = i < rParts.length && !rParts[i].isEmpty() ? Integer.parseInt(rParts[i]) : 0;
            int cVal = i < cParts.length && !cParts[i].isEmpty() ? Integer.parseInt(cParts[i]) : 0;
            if (rVal > cVal) return true;
            if (rVal < cVal) return false;
        }
        return false;
    }

    private static void showUpdateDialog(Activity activity, String tag, String title, String notes, String downloadUrl, long sizeBytes) {
        String sizeText = sizeBytes > 0 ? String.format(" (%.1f MB)", sizeBytes / (1024.0 * 1024.0)) : "";
        StringBuilder msg = new StringBuilder();
        msg.append("A new version of AirBuds (").append(tag).append(") is available").append(sizeText).append(".\n\n");
        if (!notes.isEmpty()) {
            msg.append("What's New:\n").append(notes).append("\n\n");
        }
        msg.append("Would you like to download and install it now?");

        new AlertDialog.Builder(activity)
                .setTitle("Update Available")
                .setMessage(msg.toString())
                .setPositiveButton("Download & Install", (dialog, which) -> downloadAndInstall(activity, tag, downloadUrl))
                .setNegativeButton("Later", null)
                .show();
    }

    @SuppressWarnings("deprecation")
    private static void downloadAndInstall(Activity activity, String tag, String downloadUrl) {
        ProgressDialog progress = new ProgressDialog(activity);
        progress.setTitle("Downloading Update");
        progress.setMessage("Downloading AirBuds " + tag + "...");
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setIndeterminate(false);
        progress.setMax(100);
        progress.setCancelable(false);
        progress.show();

        new Thread(() -> {
            HttpURLConnection conn = null;
            FileOutputStream fos = null;
            InputStream is = null;
            try {
                URL url = new URL(downloadUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setInstanceFollowRedirects(true);
                conn.setRequestProperty("User-Agent", "AirBuds-Android-App");
                conn.connect();

                // Handle redirects manually if needed
                int status = conn.getResponseCode();
                if (status == HttpURLConnection.HTTP_MOVED_TEMP
                        || status == HttpURLConnection.HTTP_MOVED_PERM
                        || status == HttpURLConnection.HTTP_SEE_OTHER
                        || status == 307) {
                    String redirectUrl = conn.getHeaderField("Location");
                    conn.disconnect();
                    url = new URL(redirectUrl);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestProperty("User-Agent", "AirBuds-Android-App");
                    conn.connect();
                }

                int fileLength = conn.getContentLength();
                is = conn.getInputStream();
                File outputFile = new File(activity.getCacheDir(), "AirBuds_update.apk");
                if (outputFile.exists()) {
                    outputFile.delete();
                }
                fos = new FileOutputStream(outputFile);

                byte[] buffer = new byte[8192];
                long total = 0;
                int count;
                while ((count = is.read(buffer)) != -1) {
                    total += count;
                    if (fileLength > 0) {
                        int percent = (int) (total * 100 / fileLength);
                        new Handler(Looper.getMainLooper()).post(() -> progress.setProgress(percent));
                    }
                    fos.write(buffer, 0, count);
                }
                fos.flush();

                new Handler(Looper.getMainLooper()).post(() -> {
                    if (!activity.isFinishing()) {
                        progress.dismiss();
                        promptInstall(activity, outputFile);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Download failed", e);
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (!activity.isFinishing()) {
                        progress.dismiss();
                        Toast.makeText(activity, "Download failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            } finally {
                try { if (is != null) is.close(); } catch (Exception ignored) {}
                try { if (fos != null) fos.close(); } catch (Exception ignored) {}
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private static void promptInstall(Activity activity, File apkFile) {
        if (!apkFile.exists()) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.getPackageManager().canRequestPackageInstalls()) {
                new AlertDialog.Builder(activity)
                        .setTitle("Install Permission Required")
                        .setMessage("To install the AirBuds update, please allow installation from this app in Settings.")
                        .setPositiveButton("Open Settings", (d, w) -> {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + activity.getPackageName()));
                            activity.startActivity(intent);
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                return;
            }
        }

        try {
            Uri apkUri = Uri.parse("content://" + activity.getPackageName() + ".fileprovider/" + apkFile.getName());
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Install trigger failed", e);
            Toast.makeText(activity, "Failed to launch installer: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static void showToast(Activity activity, String msg) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (!activity.isFinishing()) {
                Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
