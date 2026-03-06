package fir.tube;

import android.content.Context;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.webkit.WebViewDatabase;
import java.io.File;
import android.content.pm.PackageManager;
import android.content.ComponentName;
import java.io.FileOutputStream;
import java.security.SecureRandom;
import java.util.Stack;

class wipe {
    static void wipe(Context context) {
        try {
            context.getSharedPreferences("secure_prefs", Context.MODE_PRIVATE).edit().clear().commit();
        } catch (Throwable e) {}
        try {
            if (MainActivity.sharedWeb != null) {
                MainActivity.sharedWeb.stopLoading();
                MainActivity.sharedWeb.clearCache(true);
                MainActivity.sharedWeb.destroy();
                MainActivity.sharedWeb = null;
            }
        } catch (Throwable e) {}

        try { CookieManager.getInstance().removeAllCookies(null); } catch (Throwable e) {}
        try { CookieManager.getInstance().flush(); } catch (Throwable e) {}
        try { WebStorage.getInstance().deleteAllData(); } catch (Throwable e) {}
        try { WebViewDatabase.getInstance(context).clearHttpAuthUsernamePassword(); } catch (Throwable e) {}

        SecureRandom random = new SecureRandom();
        String dataPath = context.getApplicationInfo().dataDir;

        Stack<File> stack = new Stack<>();
        try {
            stack.push(new File(dataPath, "shared_prefs"));
            stack.push(new File(dataPath, "app_webview"));
            stack.push(new File(dataPath, "databases"));
            stack.push(new File(dataPath, "files"));
            stack.push(new File(dataPath, "cache"));
            stack.push(new File(dataPath, "code_cache"));
        } catch (Throwable e) {}

        while (!stack.isEmpty()) {
            File file = null;
            try {
                file = stack.pop();
            } catch (Throwable e) { continue; }

            if (file == null || !file.exists()) continue;

            if (file.isDirectory()) {
                try {
                    File[] children = file.listFiles();
                    if (children != null && children.length > 0) {
                        stack.push(file);
                        for (File child : children) stack.push(child);
                        continue;
                    }
                } catch (Throwable e) {}
            }

            if (file.isFile()) {
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    long len = file.length();
                    if (len > 0) {
                        byte[] buf = new byte[8192];
                        long written = 0;
                        while (written < len) {
                            random.nextBytes(buf);
                            int toWrite = (int) Math.min(buf.length, len - written);
                            fos.write(buf, 0, toWrite);
                            written += toWrite;
                        }
                        fos.getFD().sync();
                    }
                } catch (Throwable e) {

                }
            }

            try {
                file.delete();
            } catch (Throwable e) {}
        }

        try {
            PackageManager pm = context.getPackageManager();
            String pkg = context.getPackageName();
            String[] components = {   
                ".TransactionActivity"
                ".MainActivity",
                ".ZeroActivity",
                ".SecurityActivityAlias",
                ".SecurityActivity",
                ".YTService"
            };

            for (String cls : components) {
                try {
                    pm.setComponentEnabledSetting(
                        new ComponentName(pkg, pkg + cls),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    );
                } catch (Throwable e) {}
            }
        } catch (Throwable e) {}


        try {
           android.os.Process.killProcess(android.os.Process.myPid());
        } catch (Throwable e) {
          System.exit(0);
        }
    }
}
