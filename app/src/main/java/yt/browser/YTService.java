package yt.browser;

import android.app.*;
import android.content.*;
import android.os.*;
import android.webkit.*;
import android.content.pm.ServiceInfo;
import java.util.List;

public class YTService extends Service {
    static Context serviceContext;

    @Override
    public void onCreate() {
        super.onCreate();
        serviceContext = this;
    }

   static void setupWebStatic(WebView v) {
    WebSettings s = v.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setMediaPlaybackRequiresUserGesture(false);
    v.setWebViewClient(new WebViewClient() {
        @Override
        public void onPageFinished(WebView view, String url) {
            view.evaluateJavascript(
                "setInterval(() => {" +
                "  const video = document.querySelector('video');" +
                "  const player = document.querySelector('#movie_player');" +
                "  if (!video || !player) return;" +
                "  const isAd = player.classList.contains('ad-showing') || player.classList.contains('ad-interrupting');" +
                "  if (!isAd && isFinite(video.duration) && video.duration > 0) {" +
                "  if ("+ZeroActivity.rep+"==true){"+
                "    if (video.duration - video.currentTime <= 0.4) {" +
                "      video.currentTime = 0;" +
                "      video.play();" +
                "    }}" +
                "  }" +
                "  if (isAd) {" +
                "    video.muted = true;" +
                "    if (isFinite(video.duration) && video.duration > 0) {" +
                "      video.currentTime = video.duration + 1;" +
                "    }" +
                "    const skipBtn = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern');" +
                "    if (skipBtn) skipBtn.click();" +
                "    video.play();" +
                "  } else {" +
                "    if (video.muted) video.muted = false;" +
                "  }" +
                "  const selectors = ['.ytp-ad-overlay-container', '.ytp-ad-message-container', 'ytm-companion-ad-renderer', 'ytm-promoted-sparkles-web-renderer', '.ad-unit', 'ytm-promoted-video-renderer'];" +
                "  selectors.forEach(selector => {" +
                "    const el = document.querySelector(selector);" +
                "    if (el) el.remove();" +
                "  });" +
                "}, 200);", null);
        }
    });
    v.loadUrl("https://m.youtube.com");
}

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startEnforcedService();

        new Thread(() -> {
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            boolean wasInBack = true;
            while (true) {
                SystemClock.sleep(500);
                boolean isFore = isAppForeground(am);
                if (wasInBack && isFore) {
                    MainActivity.keepBlocking = false;
                    wasInBack = false;
                } 
                if (!isFore) wasInBack = true;
            }
        }).start();

        return START_STICKY;
    }

    private boolean isAppForeground(ActivityManager am) {
        List<ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
        if (procs == null) return false;
        for (ActivityManager.RunningAppProcessInfo p : procs) {
            if (p.processName.equals(getPackageName())) {
                return p.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
            }
        }
        return false;
    }

    private void startEnforcedService() {
        NotificationChannel chan = new NotificationChannel("yt", "Svc", NotificationManager.IMPORTANCE_LOW);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(chan);
        Notification n = new Notification.Builder(this, "yt")
                .setContentTitle("Engine Active")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .build();

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, n);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        wipe.wipe(YTService.this);
        return new Binder();
    }
}
