package fir.tube;

import android.app.ActivityManager;
import java.util.List;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.FrameLayout;
import android.webkit.WebView;

public class MainActivity extends Activity {
    static WebView sharedWeb;
    private FrameLayout root;

    static View fsView;
    static android.webkit.WebChromeClient.CustomViewCallback fsCallback;
    static int fsPrevOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;

    private void initControlPanel() {
    try {
        if (root == null) {
            root = new android.widget.FrameLayout(this);
        }

        android.widget.LinearLayout mainLayout = new android.widget.LinearLayout(this);
        mainLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
        mainLayout.setLayoutParams(new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, 
                android.view.ViewGroup.LayoutParams.MATCH_PARENT));

        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        float density = dm.density;

        int safeMarginPx = (int) (44 * density); 
        int cornerRadius = 0;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            android.view.RoundedCorner topCorner = getWindowManager().getDefaultDisplay()
                    .getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_RIGHT);
            if (topCorner != null) {
                cornerRadius = topCorner.getRadius();
            }
        }

        int finalXY = Math.max(cornerRadius, safeMarginPx);

        android.widget.RelativeLayout topPanel = new android.widget.RelativeLayout(this);
        topPanel.setBackgroundColor(android.graphics.Color.parseColor("#1A1A1A"));
        topPanel.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, finalXY));

        android.widget.TextView menuButton = new android.widget.TextView(this);
        menuButton.setText("⋮"); 
        menuButton.setTextColor(android.graphics.Color.WHITE);
        menuButton.setTextSize(22);
        menuButton.setIncludeFontPadding(false);
        menuButton.setGravity(android.view.Gravity.CENTER);

        android.widget.RelativeLayout.LayoutParams btnParams = new android.widget.RelativeLayout.LayoutParams(
                finalXY, finalXY);
        btnParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_RIGHT);
        btnParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP);
        menuButton.setLayoutParams(btnParams);
        
        menuButton.setOnClickListener(v -> {
            android.widget.PopupMenu popup = new android.widget.PopupMenu(this, v);
            popup.getMenu().add(0, 1, 0, "Settings");
            popup.getMenu().add(0, 2, 0, "Back");
            popup.getMenu().add(0, 3, 0, "Restart");

            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == 1) {
                    Intent i = new Intent(this, ZeroActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
                    startActivity(i);
                    moveTaskToBack(true);
                } else if (id == 2) {
                    if (sharedWeb != null && sharedWeb.canGoBack()) sharedWeb.goBack();
                } else if (id == 3) {
                    if (sharedWeb != null) sharedWeb.reload();
                }
                return true;
            });
            popup.show();
        });

        android.view.ViewParent parent = root.getParent();
        if (parent instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) parent).removeView(root);
        }
        
        android.widget.LinearLayout.LayoutParams webParams = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        root.setLayoutParams(webParams);

        topPanel.addView(menuButton);
        mainLayout.addView(topPanel);
        mainLayout.addView(root);

        setContentView(mainLayout);
        
    } catch (Exception e) {
        
    }}

    private boolean isAppForeground() {
    ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
    java.util.List<ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
    if (procs == null) return false;
    for (ActivityManager.RunningAppProcessInfo p : procs) {
        if (p.processName.equals(getPackageName())) {
            return p.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
        }
    }
    return false;
    }
    
    static class MyWebView extends WebView {
        MyWebView(Context context) {
            super(context);
        }

        @Override
        protected void onWindowVisibilityChanged(int visibility) {
            if (ZeroActivity.back) {    
                super.onWindowVisibilityChanged(View.VISIBLE);
                if (visibility != View.VISIBLE) detachFromUI();                          
            } else {
                super.onWindowVisibilityChanged(visibility);
            }
        }

        @Override
        public void onWindowFocusChanged(boolean hasWindowFocus) {
            if (ZeroActivity.back) {                                        
                super.onWindowFocusChanged(true); 
                if (!hasWindowFocus) detachFromUI();
            } else {
                super.onWindowFocusChanged(hasWindowFocus);
            }
        }
    }

    @Override
    protected void onCreate(Bundle b) {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onCreate(b);
        root = new FrameLayout(this);
        initControlPanel(); 
        
        if (sharedWeb == null) {
            sharedWeb = new MyWebView(getApplicationContext());
            YTService.setupWebStatic(sharedWeb);
        }        
        sharedWeb.setWebChromeClient(chrome);
        attachToUI();
        if (fsView != null) showFullscreenView();        
        startForegroundService(new Intent(this, YTService.class));
    }

    private void attachToUI() {
        try {        
        if (sharedWeb != null && sharedWeb.getParent() == null) root.addView(sharedWeb);
        } catch (Throwable t) {}
    }

    private static void detachFromUI() {
    try {
    if (sharedWeb != null && sharedWeb.getParent() != null) ((ViewGroup) sharedWeb.getParent()).removeView(sharedWeb);
    } catch (Throwable t) {}
    }

    @Override
    protected void onPause() {
        super.onPause();           
          if (!isAppForeground()) {
             ZeroActivity.wait = true;
          }               
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    if (hasFocus) attachToUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        attachToUI();
        if (ZeroActivity.wait) {
           Intent i = new Intent(this, SecurityActivity.class);
           i.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
           startActivity(i);
           moveTaskToBack(true);
        }
                
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE | 
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | 
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | 
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | 
            View.SYSTEM_UI_FLAG_FULLSCREEN | 
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
    }
  
    private final android.webkit.WebChromeClient chrome = new android.webkit.WebChromeClient() {
    @Override
    public void onShowCustomView(View view, CustomViewCallback callback) {
        if (fsView != null) {
            callback.onCustomViewHidden();
            return;
        }
        fsView = view;
        fsCallback = callback;
        fsPrevOrientation = getRequestedOrientation();
        showFullscreenView();
    }

    @Override
    public void onHideCustomView() {
        hideFullscreenView(false);
    }

    @Override
    public android.graphics.Bitmap getDefaultVideoPoster() {
        return android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888);
    } };

    private void showFullscreenView() {
    if (fsView == null) return;
    ViewGroup decor = (ViewGroup) getWindow().getDecorView();

    ViewParent p = fsView.getParent();
    if (p instanceof ViewGroup) {
        ((ViewGroup) p).removeView(fsView);
    }

    fsView.setBackgroundColor(android.graphics.Color.BLACK);
    decor.addView(fsView, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);    

    }


    private void hideFullscreenView(boolean notifyPage) {
    if (fsView == null) return;
    View v = fsView;
    android.webkit.WebChromeClient.CustomViewCallback cb = fsCallback;
    fsView = null;
    fsCallback = null;

    ViewParent p = v.getParent();
    if (p instanceof ViewGroup) {
        ((ViewGroup) p).removeView(v);
    }

    setRequestedOrientation(fsPrevOrientation);
    if (notifyPage && cb != null) cb.onCustomViewHidden();
    }
         
    @Override
    public void onBackPressed() {
    if (fsView != null) {
        hideFullscreenView(true);
        return;
    }
    moveTaskToBack(true);
    }
    
}
