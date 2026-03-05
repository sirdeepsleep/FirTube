package fir.tube;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.*;

public class TransactionActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onCreate(savedInstanceState);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        getWindow().getDecorView().setSystemUiVisibility(
			View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
			| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
			| View.SYSTEM_UI_FLAG_FULLSCREEN
			| View.SYSTEM_UI_FLAG_LAYOUT_STABLE
			| View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
			| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );    

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();

        if (!Intent.ACTION_MAIN.equals(intent.getAction()) || 
            !intent.hasCategory(Intent.CATEGORY_LAUNCHER) || 
            (extras != null && !extras.isEmpty())) {
            finishAndRemoveTask();
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(0);
            return;
        }

        SharedPreferences prefs = getSharedPreferences("first_launch_prefs", MODE_PRIVATE);

        if (!prefs.getBoolean("welcome_done", false)) {
            showWelcomeDialog(prefs);
        } else {
            startSecurityAndFinish();
        }
    }

    @Override
    protected void onPause() {
       super.onPause();
       finishAndRemoveTask();
    }

    private void showWelcomeDialog(final android.content.SharedPreferences prefs) {
    android.app.AlertDialog d = new android.app.AlertDialog.Builder(this)
            .setTitle("Hello!")
            .setMessage("FirTube — browser based on Android webView for watching YouTube.\n\n" +
                    "Features:\n" +
                    "— Background play mode.\n" +
                    "— Ad blocking and skipping.\n" +
                    "— Video auto-repeat.\n" +
                    "— Password for entry and password that wipes data + suspends access to the application (for emergency situations).")
            .setPositiveButton("OK", (dialog, which) -> {
                prefs.edit().putBoolean("welcome_done", true).apply();
                startSecurityAndFinish();
            })
            .setCancelable(false)
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


    private void startSecurityAndFinish() {
        Intent securityIntent = new Intent(this, SecurityActivity.class);
        securityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        startActivity(securityIntent);
        finishAndRemoveTask();
    }
}
