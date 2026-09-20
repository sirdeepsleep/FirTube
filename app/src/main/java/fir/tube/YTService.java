package fir.tube;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.*;
import android.webkit.*;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.regex.Pattern;

public class YTService extends Service {

    static volatile WebView web;
    static volatile boolean bg = true;

    private boolean wantPlay = false;
    private int resumeTries = 0;

    private static final int NOTIF_ID = 1;
    private static final long POLL_MS = 1000;

    private static final String A_PLAY  = "fir.tube.PLAY";
    private static final String A_PAUSE = "fir.tube.PAUSE";
    private static final String A_PREV  = "fir.tube.PREV";
    private static final String A_NEXT  = "fir.tube.NEXT";
    private static final String A_REW   = "fir.tube.REW";
    private static final String A_FF    = "fir.tube.FF";
    private static final String A_STOP  = "fir.tube.STOP";

    private MediaSession session;
    private NotificationManager nm;
    private String channelId;
    private boolean foreground = false;
    private final Handler h = new Handler(Looper.getMainLooper());

    private boolean playing = false;
    private long posMs = 0;
    private long durMs = -1;
    private String title = "YouTube";
    private String artist = "";

    static final boolean DEBUG = true;

    static final boolean HIDE_ON_LOCKSCREEN = true;

    private boolean sessionOn = true;

    private long lastPos = -1;
    private int stuck = 0;
    private int nudges = 0;

    private static final Pattern AD_HOSTS = Pattern.compile(
        "(^|.*\\.)(doubleclick\\.net|googlesyndication\\.com|googleadservices\\.com|" +
        "googletagservices\\.com|2mdn\\.net|imasdk\\.googleapis\\.com|moatads\\.com|" +
        "adservice\\.google\\.[a-z.]+)$");

    static boolean isAdRequest(Uri u) {
        String host = u.getHost();
        if (host == null) return false;
        if (AD_HOSTS.matcher(host).matches()) return true;
        if (host.endsWith("youtube.com")) {
            String p = u.getPath();
            if (p == null) return false;
            return p.startsWith("/pagead/")
                || p.startsWith("/api/stats/ads")
                || p.startsWith("/ptracking")
                || p.startsWith("/pcs/activeview")
                || p.contains("/get_midroll_info")
                || p.contains("/ad_break");
        }
        return false;
    }

    static WebResourceResponse emptyResponse() {
        return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
    }

   private static final String AD_CSS_PLAIN =
        "ytm-promoted-sparkles-web-renderer,ytm-promoted-sparkles-text-search-renderer," +
        "ytm-promoted-video-renderer,ytm-compact-promoted-video-renderer,ytm-companion-ad-renderer," +
        "ytm-ad-slot-renderer,ytm-in-feed-ad-layout-renderer,ytm-display-ad-renderer," +
        "ytm-brand-video-singleton-renderer,ytm-brand-video-shelf-renderer," +
        "ytm-video-masthead-ad-v3-renderer,ytm-banner-promo-renderer,ytm-mealbar-promo-renderer," +
        "ytm-search-pyv-renderer,ytd-ad-slot-renderer,ytd-in-feed-ad-layout-renderer," +
        "ytd-promoted-sparkles-web-renderer,ytd-display-ad-renderer,ytd-banner-promo-renderer," +
        "ytd-companion-slot-renderer,#masthead-ad,#player-ads,.ad-unit," +
        ".ytp-ad-overlay-container,.ytp-ad-overlay-slot,.ytp-ad-message-container," +
        ".ytp-ad-image-overlay,.ytp-ad-text-overlay";

    private static final String AD_CSS_HAS =
        "ytm-rich-item-renderer:has(ytm-ad-slot-renderer)," +
        "ytm-rich-item-renderer:has(ytm-in-feed-ad-layout-renderer)," +
        "ytm-rich-item-renderer:has(ytm-display-ad-renderer)," +
        "ytm-rich-item-renderer:has(ytm-promoted-sparkles-web-renderer)";

    static void injectVisibility(WebView v) {
        if (v == null) return;
        v.evaluateJavascript(
            "(function(){if(window.__visSpoof)return;window.__visSpoof=true;" +
            "var g=null;try{g=Object.getOwnPropertyDescriptor(Document.prototype,'visibilityState');}catch(e){}" +
            "window.__realVis=function(){try{return g.get.call(document);}catch(e){return '?';}};" +
            "var def=function(o,k,val){try{Object.defineProperty(o,k,{get:function(){return val;},configurable:true});}catch(e){}};" +
            "[document,Document.prototype].forEach(function(o){" +
            "def(o,'hidden',false);def(o,'webkitHidden',false);" +
            "def(o,'visibilityState','visible');def(o,'webkitVisibilityState','visible');});" +
            "document.hasFocus=function(){return true;};" +
            "try{Object.defineProperty(document,'onvisibilitychange',{get:function(){return null;},set:function(){},configurable:true});}catch(e){}" +
            "var stop=function(e){e.stopImmediatePropagation();};" +
            "var stopWin=function(e){if(e.target===window||e.target===document)e.stopImmediatePropagation();};" +
            "window.addEventListener('visibilitychange',stop,true);" +
            "window.addEventListener('webkitvisibilitychange',stop,true);" +
            "document.addEventListener('visibilitychange',stop,true);" +
            "window.addEventListener('freeze',stop,true);window.addEventListener('resume',stop,true);" +
            "window.addEventListener('blur',stopWin,true);window.addEventListener('focus',stopWin,true);" +
            // и не даём странице подписаться на эти события после нас
            "var oa=EventTarget.prototype.addEventListener;" +
            "EventTarget.prototype.addEventListener=function(t,l,o){" +
            "if(t==='visibilitychange'||t==='webkitvisibilitychange'||t==='freeze'||t==='resume')return;" +
            "return oa.call(this,t,l,o);};" +
            "setInterval(function(){try{window._lact=Date.now();}catch(e){}" +
            "try{if(window.yt&&yt.util&&yt.util.activity&&yt.util.activity.setTimestamp)" +
            "yt.util.activity.setTimestamp(Date.now());}catch(e){}},30000);" +
            (DEBUG ?
            "var lg=function(e){var m=e.target;console.log('YTDBG '+e.type+' t='+Math.round((m&&m.currentTime)||0)" +
            "+' err='+((m&&m.error)?m.error.code:0)+' rs='+(m&&m.readyState)+' ns='+(m&&m.networkState));};" +
            "['play','pause','waiting','stalled','suspend','emptied','error','ended'].forEach(function(n){" +
            "document.addEventListener(n,lg,true);});"
            : "") +
            "})();", null);
    }

    static void injectAdblock(WebView v) {
        if (v == null) return;
        boolean vid = ZeroActivity.VideoAdsSkip == true;
        boolean ban = ZeroActivity.bannerBlock == true;
        boolean rep = ZeroActivity.rep == true;

        v.evaluateJavascript(
            "(function(){" +
            "var F=window.__f=window.__f||{};F.vid=" + vid + ";F.ban=" + ban + ";F.rep=" + rep + ";" +
            "if(window.__adInit){if(window.__adTick)window.__adTick();return;}" +
            "window.__adInit=true;" +
            "var DEL=['adPlacements','playerAds','adSlots','adBreakHeartbeatParams'];" +
            "var AD={adSlotRenderer:1,promotedSparklesWebRenderer:1,promotedSparklesTextSearchRenderer:1," +
            "promotedVideoRenderer:1,compactPromotedVideoRenderer:1,displayAdRenderer:1,inFeedAdLayoutRenderer:1," +
            "brandVideoSingletonRenderer:1,brandVideoShelfRenderer:1,videoMastheadAdV3Renderer:1," +
            "bannerPromoRenderer:1,mealbarPromoRenderer:1,searchPyvRenderer:1};" +
            "function ad(k){return Object.prototype.hasOwnProperty.call(AD,k);}" +
            "function isObj(x){return !!x&&typeof x==='object'&&!Array.isArray(x);}" +
            "function isAd(e){if(!isObj(e))return false;" +
            "for(var k in e){if(ad(k))return true;var x=e[k];" +
            "if(isObj(x)){for(var k2 in x){if(ad(k2))return true;" +
            "if(k2==='content'&&isObj(x[k2])){for(var k3 in x[k2]){if(ad(k3))return true;}}}}}" +
            "return false;}" +
            "function clean(o,d){if(!o||typeof o!=='object'||d>60)return;" +
            "if(Array.isArray(o)){" +
            "if(F.ban){for(var i=o.length-1;i>=0;i--){if(isAd(o[i]))o.splice(i,1);}}" +
            "for(var j=0;j<o.length;j++)clean(o[j],d+1);return;}" +
            "if(F.vid){for(var k=0;k<DEL.length;k++){if(DEL[k] in o)delete o[DEL[k]];}}" +
            "for(var key in o)clean(o[key],d+1);}" +
            "function yt(r){return !!r&&typeof r==='object'&&(!!(r.responseContext||r.playerResponse||" +
            "r.adPlacements||r.playerAds||r.onResponseReceivedActions||r.onResponseReceivedEndpoints||r.contents)" +
            "||(Array.isArray(r)&&r.length<10));}" +            
            "try{var op=JSON.parse;JSON.parse=function(){var r=op.apply(this,arguments);" +
            "try{if(yt(r))clean(r,0);}catch(e){}return r;};}catch(e){}" +
            "try{if(window.Response&&Response.prototype.json){var oj=Response.prototype.json;" +
            "Response.prototype.json=function(){return oj.apply(this,arguments).then(function(j){" +
            "try{if(yt(j))clean(j,0);}catch(e){}return j;});};}}catch(e){}" +
            "['ytInitialPlayerResponse','ytInitialData'].forEach(function(n){try{" +
            "if(window[n]){clean(window[n],0);return;}var val;" +
            "Object.defineProperty(window,n,{configurable:true,get:function(){return val;}," +
            "set:function(x){try{clean(x,0);}catch(e){}val=x;}});}catch(e){}});" +
            "var CSS='" + AD_CSS_PLAIN + "{display:none!important}" + AD_CSS_HAS + "{display:none!important}';" +
            "var ADSEL='" + AD_CSS_PLAIN + "';" +
            "function css(){var st=document.getElementById('__adcss');" +
            "if(!F.ban){if(st)st.remove();return;}if(st)return;" +
            "st=document.createElement('style');st.id='__adcss';st.textContent=CSS;" +
            "(document.head||document.documentElement).appendChild(st);}" +
            "var SKIP='.ytp-skip-ad-button,.ytp-ad-skip-button,.ytp-ad-skip-button-modern," +
            ".ytp-ad-skip-button-slot button,.ytp-ad-overlay-close-button';" +
            "var n=0;" +
            "function tick(){" +
            "css();" +
            "if(F.ban&&(++n%5===0)){var els=document.querySelectorAll(ADSEL);" +
            "for(var q=0;q<els.length;q++){var t=els[q].tagName||'';" +
            "var c=(t.indexOf('YT')===0)?(els[q].closest('ytm-rich-item-renderer')||els[q]):els[q];" +
            "c.style.setProperty('display','none','important');}}" +
            "var p=document.querySelector('#movie_player');" +
            "var v=document.querySelector('#movie_player video')||document.querySelector('video');" +
            "if(p&&!p.__obs){p.__obs=1;try{new MutationObserver(tick).observe(p,{attributes:true,attributeFilter:['class']});}catch(e){}}" +
            "if(!p||!v)return;" +
            "var isAd=p.classList.contains('ad-showing')||p.classList.contains('ad-interrupting');" +
            "if(isAd&&F.vid){" +
            "if(!window.__inAd){window.__inAd=true;window.__prevRate=v.playbackRate;window.__wasMuted=v.muted;}" +
            "v.muted=true;try{v.playbackRate=16;}catch(e){}" +
            "var d=v.duration;" +
            "if(isFinite(d)&&d>0&&v.currentTime<d-0.05){try{v.currentTime=d;}catch(e){}}" +
            "var b=document.querySelectorAll(SKIP);" +
            "for(var i=0;i<b.length;i++){try{b[i].click();}catch(e){}}" +
            "if(v.paused){try{v.play();}catch(e){}}" +
            "}else{" +
            "if(window.__inAd){window.__inAd=false;try{v.playbackRate=window.__prevRate||1;}catch(e){}" +
            "v.muted=!!window.__wasMuted;}" +
            "if(F.rep&&isFinite(v.duration)&&v.duration>0&&v.duration-v.currentTime<=0.4){" +
            "v.currentTime=0;v.play();}" +
            "}}" +
            "window.__adTick=tick;" +
            "clearInterval(window.ytTimer);window.ytTimer=setInterval(tick,100);" +
            "tick();" +
            "})();", null);
    }

    static void injectLogic(WebView v) {
        injectVisibility(v);
        injectAdblock(v);
    }

    static void setupWebStatic(WebView v) {
        web = v;
        if (Build.VERSION.SDK_INT >= 26) {
            v.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false);
        }
        WebSettings s = v.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        v.setWebViewClient(new WebViewClient() {

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri url = request.getUrl();
                if (url == null) return super.shouldInterceptRequest(view, request);

                if ((ZeroActivity.VideoAdsSkip==true || ZeroActivity.bannerBlock==true) && isAdRequest(url)) {
                    return emptyResponse();
                }

                String host = url.getHost();

                if (host != null) {
                    boolean isEssential = (ZeroActivity.BlockUnknownLinksAndProtocols==false) || host.matches("(^|.*\\.)(youtube|youtube-nocookie|google|googlevideo|gstatic|ytimg|ggpht)\\.[a-z.]+$");

                    if (!isEssential) {
                        return emptyResponse();
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
                injectVisibility(view);
                injectAdblock(view);
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                injectVisibility(view);
                injectAdblock(view);
            }

            @Override
            public void onPageCommitVisible(WebView view, String url) {
                injectVisibility(view);
                injectAdblock(view);
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                android.util.Log.w("YTDBG", "renderer gone, crashed=" + detail.didCrash());
                MainActivity.onWebDied(view);
                return true; // не роняем приложение
            }
        });
        v.loadUrl("https://m.youtube.com");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        ensureWeb();

        session = new MediaSession(this, "YTService");
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay()            { command(A_PLAY); }
            @Override public void onPause()           { command(A_PAUSE); }
            @Override public void onSkipToNext()      { command(A_NEXT); }
            @Override public void onSkipToPrevious()  { command(A_PREV); }
            @Override public void onRewind()          { command(A_REW); }
            @Override public void onFastForward()     { command(A_FF); }
            @Override public void onStop()            { command(A_STOP); }
            @Override public void onCustomAction(String action, Bundle extras) { command(action); }

            @Override
            public void onSeekTo(long pos) {
                runJs("var v=document.querySelector('video');if(v)v.currentTime=" + (pos / 1000.0) + ";");
                posMs = pos;
                pushPlaybackState();
                pollSoon();
            }
        }, h);

        pushMetadata();
        pushPlaybackState();
        session.setActive(true);

        h.post(pollRunnable);
    }

    private void ensureWeb() {
        if (MainActivity.sharedWeb != null) return;
        MainActivity.sharedWeb = new MainActivity.MyWebView(getApplicationContext());
        setupWebStatic(MainActivity.sharedWeb);
        WebHost.attach(getApplicationContext(), MainActivity.sharedWeb, null);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!foreground) goForeground();
        if (intent != null && intent.getAction() != null) {
            command(intent.getAction());
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        h.removeCallbacksAndMessages(null);
        if (session != null) {
            session.setActive(false);
            session.release();
            session = null;
        }
        super.onDestroy();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (DEBUG) android.util.Log.d("YTDBG", "onTrimMemory level=" + level + " bg=" + bg);
    }

    @Override
    public IBinder onBind(Intent intent) {
        wipe.wipe(YTService.this);
        return new Binder();
    }

    private void runJs(String body) {
        WebView v = web;
        if (v == null) return;
        v.evaluateJavascript("(function(){" + body + "})()", null);
    }

    private void command(String a) {
        if (A_PLAY.equals(a)) {
            wantPlay = true;
            resumeTries = 0;
        } else if (A_PAUSE.equals(a) || A_STOP.equals(a)) {
            wantPlay = false;
        }

        if (A_PLAY.equals(a)) {
            runJs("var v=document.querySelector('video');if(v)v.play();");
        } else if (A_PAUSE.equals(a)) {
            runJs("var v=document.querySelector('video');if(v)v.pause();");
        } else if (A_STOP.equals(a)) {
            runJs("var v=document.querySelector('video');if(v)v.pause();");
        } else if (A_REW.equals(a)) {
            runJs("var v=document.querySelector('video');if(v)v.currentTime=Math.max(0,v.currentTime-10);");
        } else if (A_FF.equals(a)) {
            runJs("var v=document.querySelector('video');" +
                  "if(v){var m=isFinite(v.duration)?v.duration:1e9;v.currentTime=Math.min(m,v.currentTime+10);}");
        } else if (A_NEXT.equals(a)) {
            runJs("var p=document.querySelector('#movie_player');if(p&&p.nextVideo)p.nextVideo();");
        } else if (A_PREV.equals(a)) {
            runJs("var v=document.querySelector('video');var p=document.querySelector('#movie_player');" +
                  "if(v&&v.currentTime>3){v.currentTime=0;}else if(p&&p.previousVideo){p.previousVideo();}");
        }
        pollSoon();
    }

    private final Runnable pollRunnable = new Runnable() {
        @Override public void run() {
            applyLockState();
            pollState();
            h.postDelayed(this, POLL_MS);
        }
    };

    private final Runnable pollOnce = new Runnable() {
        @Override public void run() { pollState(); }
    };

    private void applyLockState() {
        if (!HIDE_ON_LOCKSCREEN || session == null) return;
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        boolean locked = km != null && km.isKeyguardLocked();
        if (locked && sessionOn) {
            session.setActive(false);
            sessionOn = false;
        } else if (!locked && !sessionOn) {
            session.setActive(true);
            sessionOn = true;
        }
    }

    private void pollSoon() {
        h.removeCallbacks(pollOnce);
        h.postDelayed(pollOnce, 250);
    }

    private void pollState() {
        WebView v = web;
        if (v == null) return;
        v.evaluateJavascript(
            "(function(){var v=document.querySelector('video');" +
            "var m=navigator.mediaSession&&navigator.mediaSession.metadata;" +
            "return {p:v?!v.paused:false,e:v?v.ended:false,t:v?v.currentTime:0," +
            "d:(v&&isFinite(v.duration))?v.duration:-1," +
            "title:(m&&m.title)||document.title||'YouTube',artist:(m&&m.artist)||''};})()",
            new ValueCallback<String>() {
                @Override public void onReceiveValue(String value) {
                    if (value == null || value.equals("null")) return;
                    try {
                        JSONObject o = new JSONObject(value);
                        long dur = (long) (o.optDouble("d", -1) * 1000);
                        if (dur <= 0) dur = -1;
                        update(o.optBoolean("p"), o.optBoolean("e"),
                               (long) (o.optDouble("t", 0) * 1000),
                               dur,
                               o.optString("title", "YouTube"),
                               o.optString("artist", ""));
                    } catch (Exception ignored) {}
                }
            });
    }

    private void update(boolean p, boolean ended, long pos, long dur, String t, String a) {
        if (session == null) return;

        if (p) {
            wantPlay = true;
            resumeTries = 0;
        } else if (wantPlay && !ended) {
            if (!bg) {
                wantPlay = false;
            } else if (resumeTries < 5) {
                resumeTries++;
                runJs("var v=document.querySelector('video');if(v)v.play();");
            }
        }

        if (p && !ended && bg && pos == lastPos) {
            if (++stuck >= 4 && nudges < 3) {
                nudges++;
                stuck = 0;
                if (DEBUG) android.util.Log.d("YTDBG", "stuck in bg, nudge #" + nudges);
                runJs("var v=document.querySelector('video');if(v){var t=v.currentTime;v.currentTime=t+0.05;v.play();}");
            }
        } else {
            stuck = 0;
            if (p && pos != lastPos) nudges = 0;
        }
        lastPos = pos;

        boolean metaChanged = !t.equals(title) || !a.equals(artist) || dur != durMs;
        boolean stateChanged = p != playing;

        playing = p;
        posMs = pos;
        durMs = dur;
        title = t;
        artist = a;

        if (metaChanged) pushMetadata();
        pushPlaybackState();
        if (foreground && (metaChanged || stateChanged)) {
            nm.notify(NOTIF_ID, buildNotification());
        }
    }

    private void pushMetadata() {
        MediaMetadata.Builder b = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, artist);
        if (durMs > 0) b.putLong(MediaMetadata.METADATA_KEY_DURATION, durMs);
        session.setMetadata(b.build());
    }

    private void pushPlaybackState() {
        long actions = PlaybackState.ACTION_PLAY
                | PlaybackState.ACTION_PAUSE
                | PlaybackState.ACTION_PLAY_PAUSE
                | PlaybackState.ACTION_SEEK_TO
                | PlaybackState.ACTION_SKIP_TO_NEXT
                | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                | PlaybackState.ACTION_STOP;

        PlaybackState st = new PlaybackState.Builder()
                .setActions(actions)
                .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED,
                          posMs, playing ? 1f : 0f)
                .addCustomAction(new PlaybackState.CustomAction.Builder(
                        A_REW, "-10s", android.R.drawable.ic_media_rew).build())
                .addCustomAction(new PlaybackState.CustomAction.Builder(
                        A_FF, "+10s", android.R.drawable.ic_media_ff).build())
                .build();
        session.setPlaybackState(st);
    }

    private String ensureChannel() {
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
            activeId = "fir.tube" + Long.toHexString(new java.security.SecureRandom().nextLong());
            NotificationChannel nch = new NotificationChannel(activeId, "Media Play", NotificationManager.IMPORTANCE_LOW);
            nch.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
            nm.createNotificationChannel(nch);
        }
        return activeId;
    }

    private Notification.Action act(int icon, String label, String action, int req) {
        Intent i = new Intent(this, YTService.class).setAction(action);
        PendingIntent pi = PendingIntent.getService(this, req, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Action.Builder(Icon.createWithResource(this, icon), label, pi).build();
    }

    private Notification buildNotification() {
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, ZeroActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Action playPause = playing
                ? act(android.R.drawable.ic_media_pause, "Pause", A_PAUSE, 3)
                : act(android.R.drawable.ic_media_play,  "Play",  A_PLAY,  3);

        return new Notification.Builder(this, channelId)
                .setContentTitle(title)
                .setContentText(artist.isEmpty() ? "YouTube" : artist)
                .setSmallIcon(playing ? android.R.drawable.ic_media_play : android.R.drawable.ic_media_pause)
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setVisibility(Notification.VISIBILITY_SECRET)
                .addAction(act(android.R.drawable.ic_media_previous, "Prev", A_PREV, 1))
                .addAction(act(android.R.drawable.ic_media_rew,      "-10s", A_REW,  2))
                .addAction(playPause)
                .addAction(act(android.R.drawable.ic_media_ff,       "+10s", A_FF,   4))
                .addAction(act(android.R.drawable.ic_media_next,     "Next", A_NEXT, 5))
                .setStyle(new Notification.MediaStyle()
                        .setMediaSession(session.getSessionToken())
                        .setShowActionsInCompactView(0, 2, 4))
                .build();
    }

    private void goForeground() {
        channelId = ensureChannel();
        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIF_ID, n);
        }
        foreground = true;
    }
}
