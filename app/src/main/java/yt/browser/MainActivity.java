package yt.browser;

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

    private void initControlPanel() {
    try {
        if (root == null) {
            root = new android.widget.FrameLayout(this);
        }

        // 1. Главный контейнер
        android.widget.LinearLayout mainLayout = new android.widget.LinearLayout(this);
        mainLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
        mainLayout.setLayoutParams(new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, 
                android.view.ViewGroup.LayoutParams.MATCH_PARENT));

        // 2. Панель управления (RelativeLayout)
        android.widget.RelativeLayout topPanel = new android.widget.RelativeLayout(this);
        topPanel.setBackgroundColor(android.graphics.Color.parseColor("#1A1A1A"));
        
        float density = getResources().getDisplayMetrics().density;
        final int standardHeight = (int) (48 * density); // Высота самой полоски с кнопкой

        // Настройка параметров: высота будет подстраиваться под padding (Safe Area + 48dp)
        android.widget.LinearLayout.LayoutParams panelLP = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, 
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        topPanel.setLayoutParams(panelLP);

        // МАГИЯ: Обработка закруглений и челок через WindowInsets
        topPanel.setOnApplyWindowInsetsListener(new android.view.View.OnApplyWindowInsetsListener() {
            @Override
            public android.view.WindowInsets onApplyWindowInsets(android.view.View v, android.view.WindowInsets insets) {
                // Получаем системный отступ сверху (учитывает челку и скругления)
                int topInset = insets.getSystemWindowInsetTop();
                
                // Если система дает 0 (в полноэкранном режиме), 
                // принудительно ставим 12dp, чтобы не прилипало к краю
                int finalPadding = Math.max(topInset, (int)(12 * density));
                
                v.setPadding(0, finalPadding, 0, 0);
                return insets;
            }
        });

        // 3. Кнопка "три точки"
        android.widget.TextView menuButton = new android.widget.TextView(this);
        menuButton.setText("⋮"); 
        menuButton.setTextColor(android.graphics.Color.WHITE);
        menuButton.setTextSize(24);
        int sidePadding = (int) (16 * density);
        
        // Важно: кнопка имеет фиксированную высоту 48dp и прижата к низу topPanel
        android.widget.RelativeLayout.LayoutParams btnParams = new android.widget.RelativeLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, standardHeight);
        btnParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_RIGHT);
        
        menuButton.setLayoutParams(btnParams);
        menuButton.setPadding(sidePadding, 0, sidePadding, 0);
        menuButton.setGravity(android.view.Gravity.CENTER);
        
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

        // 4. Очистка и настройка WebView контейнера (root)
        android.view.ViewParent parent = root.getParent();
        if (parent instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) parent).removeView(root);
        }
        
        android.widget.LinearLayout.LayoutParams webParams = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        root.setLayoutParams(webParams);

        // 5. Сборка и установка
        topPanel.addView(menuButton);
        mainLayout.addView(topPanel);
        mainLayout.addView(root);

        setContentView(mainLayout);
        
        // Сообщаем системе, что мы хотим обрабатывать отступы сами
        mainLayout.requestApplyInsets();
        
    } catch (Exception e) {
        android.util.Log.e("YT_BROWSER", "Error in initControlPanel: " + e.getMessage());
        e.printStackTrace();
    }
}



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
            super.onWindowVisibilityChanged(View.VISIBLE); }
            else {super.onWindowVisibilityChanged(visibility);}
        }

        @Override
        public void onWindowFocusChanged(boolean hasWindowFocus) {
            if (ZeroActivity.back) {
            super.onWindowFocusChanged(true); }
            else {super.onWindowFocusChanged(hasWindowFocus);}
        }
    }

    @Override
    protected void onCreate(Bundle b) {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onCreate(b);
        root = new FrameLayout(this);
        initControlPanel(); 
        //setContentView(root);
        
        if (sharedWeb == null) {
            sharedWeb = new MyWebView(getApplicationContext());
            YTService.setupWebStatic(sharedWeb);
        }
        attachToUI();
        startService(new Intent(this, YTService.class));
    }

    private void attachToUI() {
        if (sharedWeb.getParent() != null) {
            ((ViewGroup) sharedWeb.getParent()).removeView(sharedWeb);
        }
        root.addView(sharedWeb);
    }

    @Override
    protected void onPause() {
        super.onPause(); 
          if (!isAppForeground()) {
        ZeroActivity.wait = true;
          }       
        if (sharedWeb != null) {
            sharedWeb.onResume();      
            sharedWeb.resumeTimers();  
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ZeroActivity.wait) {
           Intent i = new Intent(this, SecurityActivity.class);
           i.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
           startActivity(i);
           moveTaskToBack(true);
        }
        
        if (sharedWeb != null) {
            attachToUI();
            sharedWeb.onResume();
            sharedWeb.resumeTimers();
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
    
    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }
}
