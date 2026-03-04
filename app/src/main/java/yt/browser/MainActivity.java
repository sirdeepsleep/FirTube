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
        if (root == null) root = new android.widget.FrameLayout(this);

        android.widget.LinearLayout mainLayout = new android.widget.LinearLayout(this);
        mainLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
        mainLayout.setLayoutParams(new android.view.ViewGroup.LayoutParams(-1, -1));

        android.widget.RelativeLayout topPanel = new android.widget.RelativeLayout(this);
        topPanel.setBackgroundColor(android.graphics.Color.parseColor("#1A1A1A"));
        
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        float density = dm.density;
        
        // Высота панели. Сделаем чуть больше, чтобы влез закругленный угол
        int panelHeight = (int) (56 * density); 
        topPanel.setLayoutParams(new android.widget.LinearLayout.LayoutParams(-1, panelHeight));

        // Кнопка "три точки"
        android.widget.TextView menuButton = new android.widget.TextView(this);
        menuButton.setText("⋮"); 
        menuButton.setTextColor(android.graphics.Color.WHITE);
        menuButton.setTextSize(26);
        menuButton.setIncludeFontPadding(false);
        menuButton.setGravity(android.view.Gravity.CENTER); // Центруем внутри самой кнопки

        // Размер контейнера самой кнопки (чтобы область нажатия была нормальной)
        int btnSize = (int) (44 * density);
        android.widget.RelativeLayout.LayoutParams btnParams = new android.widget.RelativeLayout.LayoutParams(btnSize, btnSize);
        btnParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_RIGHT);
        btnParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP);

        // --- РАСЧЕТ РЕАЛЬНЫХ ПИКСЕЛЕЙ ДЛЯ УГЛА ---
        // Если Android 12+, пытаемся получить радиус скругления программно
        int cornerOffset = (int) (12 * density); // Дефолт для средних скруглений
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            android.view.RoundedCorner corner = getWindowManager().getDefaultDisplay().getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_RIGHT);
            if (corner != null) {
                // Берем радиус и добавляем пару пикселей "запаса", чтобы не липло впритык
                cornerOffset = corner.getRadius() + (int)(2 * density);
            }
        }

        // Выставляем маржины так, чтобы точки были СРАЗУ после закругления
        // По рисунку: точки должны быть в самой верхней правой видимой части
        btnParams.topMargin = (int) (4 * density); 
        
        // Магия тут: отступаем справа ровно столько, сколько съедает скругление
        // Если они все еще "за экраном", увеличь этот множитель (например 0.4 -> 0.6)
        int rightMarginPx = (int) (cornerOffset * 0.4f); 
        btnParams.rightMargin = rightMarginPx;

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
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
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

        if (root.getParent() != null) ((android.view.ViewGroup) root.getParent()).removeView(root);
        root.setLayoutParams(new android.widget.LinearLayout.LayoutParams(-1, 0, 1.0f));

        topPanel.addView(menuButton);
        mainLayout.addView(topPanel);
        mainLayout.addView(root);

        setContentView(mainLayout);
        
    } catch (Exception e) {
        android.util.Log.e("YT_BROWSER", "Error: " + e.getMessage());
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
