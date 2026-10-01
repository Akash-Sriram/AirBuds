package org.airbridge.tws;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.Log;

/**
 * Intercepts and suppresses duplicate Google Play Services Fast Pair battery cards
 * so that AirBuds is the single clean source of truth for battery & ANC status.
 * Requires standard user-granted Notification Access (no root required).
 */
public class AirBudsNotificationListener extends NotificationListenerService {

    private static final String TAG = "AirBudsNotifListener";
    private static final String GMS_PACKAGE = "com.google.android.gms";
    private static final String GMS_BATTERY_CHANNEL = "BATTERY_NOTIFICATION_CHANNEL";

    public static boolean isPermissionGranted(Context context) {
        String pkgName = context.getPackageName();
        String flat = Settings.Secure.getString(context.getContentResolver(), "enabled_notification_listeners");
        if (!TextUtils.isEmpty(flat)) {
            String[] names = flat.split(":");
            for (String name : names) {
                ComponentName cn = ComponentName.unflattenFromString(name);
                if (cn != null && TextUtils.equals(pkgName, cn.getPackageName())) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;
        boolean enabled = getSharedPreferences("airbridge_prefs", Context.MODE_PRIVATE)
            .getBoolean("pref_suppress_google", true);
        if (!enabled) return;

        String pkg = sbn.getPackageName();
        if (GMS_PACKAGE.equals(pkg)) {
            Notification notif = sbn.getNotification();
            if (notif != null) {
                String channelId = notif.getChannelId();
                CharSequence title = notif.extras != null ? notif.extras.getCharSequence(Notification.EXTRA_TITLE) : null;
                String titleStr = title != null ? title.toString().toLowerCase() : "";

                boolean isBatteryChannel = GMS_BATTERY_CHANNEL.equals(channelId);
                boolean isBuds = titleStr.contains("air8") || titleStr.contains("air 8") || titleStr.contains("realme");

                if (isBatteryChannel || isBuds) {
                    Log.d(TAG, "Suppressed Google Fast Pair notification: " + sbn.getKey() + " (title=" + titleStr + ")");
                    try {
                        cancelNotification(sbn.getKey());
                    } catch (Exception e) {
                        Log.w(TAG, "Error canceling notification", e);
                    }
                }
            }
        }
    }
}
