package fir.tube;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.util.Base64;
import android.view.*;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

public class SecurityActivity extends Activity {

	private Button EmergencyWipeButton; 
    private SharedPreferences bfuPrefs;
    private EditText passwordInput;
    private TextView instructionText;
    private Button actionButton;
    private boolean isSetupMode = false;
    private String tempMainHash = null;

	private void forceBind() {
    Intent intent = new Intent(this, YTService.class);
    bindService(intent, connection, Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT | Context.BIND_ABOVE_CLIENT);
    }

	private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {}
        @Override
        public void onServiceDisconnected(ComponentName name) {
          forceBind();
        }
    };
	
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
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onCreate(savedInstanceState);
		getWindow().getDecorView().setSystemUiVisibility(
			View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
			| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
			| View.SYSTEM_UI_FLAG_FULLSCREEN
			| View.SYSTEM_UI_FLAG_LAYOUT_STABLE
			| View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
			| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );    

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int top = (int) (getResources().getDisplayMetrics().heightPixels * 0.20);
        layout.setPadding(60, top, 60, 60);
        layout.setBackgroundColor(0xFF000000); 
        layout.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);

		EmergencyWipeButton = new Button(this);
        EmergencyWipeButton.setBackgroundColor(0xFFFF0000);
        EmergencyWipeButton.setTextColor(0xFF000000);    
		EmergencyWipeButton.setText("Emergency Wipe");
		EmergencyWipeButton.setVisibility(View.GONE);
		EmergencyWipeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                forceBind();
            }
        });
        layout.addView(EmergencyWipeButton);

		int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int spacerHeight = (int) (screenHeight * 0.03);
        View spacer = new View(this);
        LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(-1, spacerHeight); 
        layout.addView(spacer, spacerParams);

        instructionText = new TextView(this);
        instructionText.setTextSize(20);
        instructionText.setTextColor(0xFFFFFFFF); 
        layout.addView(instructionText);

        passwordInput = new EditText(this);
        passwordInput.setHint("Enter password here");
        passwordInput.setHintTextColor(0xFF404040);
        passwordInput.setTextColor(0xFF000000);
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(passwordInput);
        passwordInput.requestFocus();

        actionButton = new Button(this);
        actionButton.setBackgroundColor(0xFFFFFFFF);
        actionButton.setTextColor(0xFF000000);        
        layout.addView(actionButton);

        setContentView(layout);

		bfuPrefs = getSharedPreferences("secure_prefs", MODE_PRIVATE);
        actionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleButtonClick();
            }
        });

        checkState();
    }

    private void checkState() {
        if (!bfuPrefs.contains("pass_hash")) {
            isSetupMode = true;
            instructionText.setText("Set Unlock Password (that opens browser). Please set a unique password, which you don't use in other apps or accounts.\n");
            actionButton.setText("Next");
        } else {
            isSetupMode = false;
			EmergencyWipeButton.setVisibility(View.VISIBLE);
            instructionText.setText("Enter Password");
            actionButton.setText("Unlock");
        }
    }

    private void handleButtonClick() {
        String input = passwordInput.getText().toString();
        if (input.isEmpty()) return;

        if (isSetupMode) {
            String hashed = hashPassword(input);
            if (tempMainHash == null) {
                tempMainHash = hashed;
                instructionText.setText("Set Duress Password (that wipes browser data and disables access to app). Please set a unique password, which you don't use in other apps or accounts.\n");
                actionButton.setText("Finish Setup");
            } else {
                bfuPrefs.edit()
                        .putString("pass_hash", tempMainHash)
                        .putString("duress_hash", hashed)
                        .apply();
                isSetupMode = false;
                checkState();
            }
        } else {
            String storedDuress = bfuPrefs.getString("duress_hash", "");
            String storedPass = bfuPrefs.getString("pass_hash", "");

            if (verifyPassword(input, storedDuress)) {
                forceBind();
            } else if (verifyPassword(input, storedPass)) {
                try {
                    startActivity(new Intent(this, ZeroActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
				    finishAndRemoveTask();
                } catch (Throwable StateErr) {
                    Toast.makeText(this, "Error!", Toast.LENGTH_SHORT).show();
                }
            }  else {
                Toast.makeText(this, "Wrong password", Toast.LENGTH_SHORT).show();
            }
        }
        passwordInput.setText("");
    }


    static {
        System.loadLibrary("argon_security");
    }


    private native String argonHash(String password);
    private native boolean argonVerify(String input, String hash);


    private String hashPassword(String password) {
        return argonHash(password);
    }

    private boolean verifyPassword(String input, String record) {
        if (record == null || record.isEmpty()) return false;
        return argonVerify(input, record);
    }


    
}
