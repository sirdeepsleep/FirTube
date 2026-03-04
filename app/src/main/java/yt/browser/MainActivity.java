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
        
        // Высота панели
        int panelHeight = (int) (52 * density); 
        topPanel.setLayoutParams(new android.widget.LinearLayout.LayoutParams(-1, panelHeight));

        // Кнопка "три точки"
        android.widget.TextView menuButton = new android.widget.TextView(this);
        menuButton.setText("⋮"); 
        menuButton.setTextColor(android.graphics.Color.WHITE);
        menuButton.setTextSize(26);
        menuButton.setIncludeFontPadding(false);
        
        // ВАЖНО: Прижимаем текст вправо и вверх внутри вьюхи
        menuButton.setGravity(android.view.Gravity.TOP | android.view.Gravity.RIGHT);

        // Контейнер кнопки побольше, чтобы было проще попасть пальцем
        int btnWidth = (int) (40 * density);
        int btnHeight = (int) (40 * density);
        android.widget.RelativeLayout.LayoutParams btnParams = new android.widget.RelativeLayout.LayoutParams(btnWidth, btnHeight);
        
        btnParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_RIGHT);
        btnParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP);

        // --- РАБОТА С ИЗГИБОМ УГЛА ---
        // Эти значения выставляют точки ровно на границу видимости.
        // Если точки всё еще "прячутся" — увеличивай эти числа на 2-3 единицы.
        int safeRight = (int) (6 * density); // Отступ от правого физического края
        int safeTop = (int) (4 * density);   // Отступ от верхнего физического края

        btnParams.rightMargin = safeRight;
        btnParams.topMargin = safeTop;

        menuButton.setLayoutParams(btnParams);
        
        // Убираем внутренние зазоры самой TextView, чтобы символ ⋮ был в самом углу контейнера
        menuButton.setPadding(0, 0, 0, 0);
        
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
