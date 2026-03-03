package yt.browser;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.graphics.Color;

public class ZeroActivity extends Activity {

    static boolean wait = true;
    static boolean continued = false;   
    static boolean rep = false;
    static boolean back = false;
    static boolean bannerBlock = false;
    static boolean VideoAdsSkip = false;

    private void showConfirm(String title, String message, Runnable onConfirm) {
    new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton("YES [DISABLE]", (dialog, which) -> onConfirm.run())
        .setNegativeButton("CANCEL", null)
        .show();
    }

    @Override
    protected void onPause() {
       super.onPause();
       finishAndRemoveTask();
    }

       @Override
    protected void onResume() {
        super.onResume();
        if (continued) {
        Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            wait=false;
            startActivity(i);
            finishAndRemoveTask();
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
    protected void onCreate(Bundle b) {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onCreate(b); 
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(50, 50, 50, 50);

        Switch swRep = new Switch(this);
        swRep.setText("Auto-Repeat   ");
        swRep.setTextColor(Color.WHITE);
        swRep.setTextSize(20);
        swRep.setChecked(rep);
        swRep.setPadding(0, 50, 0, 50);
        swRep.setOnCheckedChangeListener((v, isChecked) -> rep = isChecked);
        root.addView(swRep);

        Switch swBack = new Switch(this);
        swBack.setText("Background Play   ");
        swBack.setTextColor(Color.WHITE);
        swBack.setTextSize(20);
        swBack.setChecked(back);
        swBack.setPadding(0, 50, 0, 50);
        swBack.setOnCheckedChangeListener((v, isChecked) -> back = isChecked);
        root.addView(swBack);

        Switch swBB = new Switch(this);
        swBB.setText("Banner Block (expiremental & may not work)  ");
        swBB.setTextColor(Color.WHITE);
        swBB.setTextSize(20);
        swBB.setChecked(bannerBlock);
        swBB.setPadding(0, 50, 0, 50);
        swBB.setOnCheckedChangeListener((v, isChecked) -> bannerBlock = isChecked);
        root.addView(swBB);

        Switch swVBS = new Switch(this);
        swVBS.setText("Video Ads Skip (expiremental & may not work)  ");
        swVBS.setTextColor(Color.WHITE);
        swVBS.setTextSize(20);
        swVBS.setChecked(VideoAdsSkip);
        swVBS.setPadding(0, 50, 0, 50);
        swVBS.setOnCheckedChangeListener((v, isChecked) -> VideoAdsSkip = isChecked);
        root.addView(swVBS);

        Button btn = new Button(this);
        btn.setText("CONTINUE");
        btn.setBackgroundColor(Color.RED);
        btn.setTextColor(Color.WHITE);
        btn.setOnClickListener(v -> {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            wait=false;
            continued=true;
            startActivity(i);
            finishAndRemoveTask();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 100, 0, 0);
        root.addView(btn, lp);
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int spacerHeight = (int) (screenHeight * 0.07);
        View spacer = new View(this);
        LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(-1, spacerHeight); 
        root.addView(spacer, spacerParams);
        setContentView(root);
    }
}
