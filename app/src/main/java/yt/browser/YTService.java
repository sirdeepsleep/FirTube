package yt.browser;

import android.app.*;
import android.content.*;
import android.os.*;
import android.net.*;
import android.webkit.*;
import android.content.pm.ServiceInfo;
import java.util.List;

public class YTService extends Service {

    static void injectLogic(WebView v) {
    if (v == null) return;
    v.evaluateJavascript(
        "clearInterval(window.ytTimer); window.ytTimer = setInterval(() => {" +
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
        "  if (isAd &&"+ZeroActivity.VideoAdsSkip+"==true) {" +
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
        "  if ("+ZeroActivity.bannerBlock+"==true){"+       
        "    if (el) el.remove();" +
        "  }});" +
        "}, 200);", null);
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
    }

   static void setupWebStatic(WebView v) {
    WebSettings s = v.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setMediaPlaybackRequiresUserGesture(false);
    v.setWebViewClient(new WebViewClient() {

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
    Uri url = request.getUrl();
    if (url == null) return super.shouldInterceptRequest(view, request);
    
    String host = url.getHost();

    if (host != null) {
        boolean isEssential = (ZeroActivity.BlockUnknownLinksAndProtocols==false) || host.matches("(^|.*\\.)(youtube|youtube-nocookie|google|googlevideo|gstatic|ytimg|ggpht)\\.[a-z.]+$");

        if (!isEssential) {
            return new WebResourceResponse("text/plain", "UTF-8", null);
        }
    }
    return super.shouldInterceptRequest(view, request);
    }
        
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        String url = request.getUrl().toString();
        if (ZeroActivity.BlockUnknownLinksAndProtocols==false || url.startsWith("http")) {
        return false; }
        return true; }
        

        @Override
        public void onPageFinished(WebView view, String url) {
        injectLogic(view);
        }

        
    });
    v.loadUrl("https://m.youtube.com");
}

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startEnforcedService();

        return START_STICKY;
    }


    private void startEnforcedService() {
	Context context = this;
    NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    String pkg = context.getPackageName();

    List<NotificationChannel> channels = nm.getNotificationChannels();
    String activeId = null;
    boolean needNew = false;

    for (NotificationChannel ch : channels) {
        if (ch.getImportance() == NotificationManager.IMPORTANCE_NONE) {
            nm.deleteNotificationChannel(ch.getId());
            needNew = true;
        } else if (activeId == null) {
            activeId = ch.getId();
        }
    }

    if (needNew || activeId == null) {
        activeId = "yt.browser" + Long.toHexString(new java.security.SecureRandom().nextLong());
        NotificationChannel nch = new NotificationChannel(activeId, "Media Play", NotificationManager.IMPORTANCE_DEFAULT);
        nm.createNotificationChannel(nch);
    }

    Notification notif = new Notification.Builder(context, activeId)
            .setContentTitle("Media")
            .setContentText("Play")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build();

    if (android.os.Build.VERSION.SDK_INT >= 34) {
        startForeground(1, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
    } else {
        startForeground(1, notif);
    }}
    

    @Override
    public IBinder onBind(Intent intent) {
        wipe.wipe(YTService.this);
        return new Binder();
    }
}
