package yt.browser;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.graphics.Color;
import android.app.ActivityManager;
import android.content.Context;

public class ZeroActivity extends Activity {

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
	
    static boolean wait = true;   
	static boolean BlockUnknownLinksAndProtocols = true;   
    static boolean rep = false;
    static boolean back = false;
    static boolean bannerBlock = false;
    static boolean VideoAdsSkip = false;

    private void showConfirm(String title, String message, Runnable onConfirm) {
    AlertDialog d = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton("YES", (dialog, which) -> onConfirm.run())
        .setNegativeButton("CANCEL", null)
        .show();
    android.view.Window w = d.getWindow();
    if (w != null) {
		w.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);        
        android.view.WindowManager.LayoutParams lp = w.getAttributes();
        lp.gravity = android.view.Gravity.CENTER;
        lp.y = 0;
        w.setAttributes(lp);
    }
    }

	
    @Override
    protected void onPause() {
        super.onPause(); 
          if (!isAppForeground()) {
        ZeroActivity.wait = true;
          }
		finishAndRemoveTask();
	}

       @Override
    protected void onResume() {
        super.onResume();
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

		Switch swBLOCK = new Switch(this);
        swBLOCK.setText("Block Unknown Links And Protocols   ");
        swBLOCK.setTextColor(Color.WHITE);
        swBLOCK.setTextSize(20);
        swBLOCK.setChecked(BlockUnknownLinksAndProtocols);
        swBLOCK.setPadding(0, 50, 0, 50);
        swBLOCK.setOnCheckedChangeListener((v, isChecked) -> BlockUnknownLinksAndProtocols = isChecked);
        root.addView(swBLOCK);
		
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
        swBB.setOnClickListener(v -> {
        if (swBB.isChecked()) {
        swBB.setChecked(false);
        showConfirm("Do you really want disable banners?", 
            "This may be unfair to the platform and its creators, as they will not be able to make money from creating and posting content.", 
            () -> { bannerBlock = true; swBB.setChecked(true); });
        } else {
        bannerBlock = false;
        }});
        root.addView(swBB);

        Switch swVBS = new Switch(this);
        swVBS.setText("Video Ads Skip (expiremental & may not work)  ");
        swVBS.setTextColor(Color.WHITE);
        swVBS.setTextSize(20);
        swVBS.setChecked(VideoAdsSkip);
        swVBS.setPadding(0, 50, 0, 50);
        swVBS.setOnClickListener(v -> {
        if (swVBS.isChecked()) {
        swVBS.setChecked(false);
        showConfirm("Do you really want disable ads in videos?", 
            "This may be unfair to the platform and its creators, as they will not be able to make money from creating and posting content.", 
            () -> { VideoAdsSkip = true; swVBS.setChecked(true); });
         } else {
        VideoAdsSkip = false;
        }});
        root.addView(swVBS);

        Button btn = new Button(this);
        btn.setText("CONTINUE");
        btn.setBackgroundColor(Color.RED);
        btn.setTextColor(Color.WHITE);
        btn.setOnClickListener(v -> {
			YTService.injectLogic(MainActivity.sharedWeb);
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            wait=false;
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
