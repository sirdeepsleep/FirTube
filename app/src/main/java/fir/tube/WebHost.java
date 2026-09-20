package fir.tube;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.SurfaceControlViewHost;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;

final class WebHost {
    private WebHost() {}

    static final boolean ENABLED = true;

    private static Object host;
    private static FrameLayout container;
    private static boolean failed = false;

    static boolean attach(Context ctx, View web, View extra) {
        if (!ENABLED || failed || web == null || Build.VERSION.SDK_INT < 30) return false;
        try {
            ensure(ctx.getApplicationContext());
            move(web);
            if (extra != null) move(extra);
            return true;
        } catch (Throwable t) {
            failed = true;
            return false;
        }
    }

    private static void ensure(Context app) {
        DisplayManager dm = (DisplayManager) app.getSystemService(Context.DISPLAY_SERVICE);
        Display d = dm.getDisplay(Display.DEFAULT_DISPLAY);
        DisplayMetrics m = new DisplayMetrics();
        d.getRealMetrics(m);

        if (host == null) {
            Context dc = app.createDisplayContext(d);
            container = new FrameLayout(dc);
            SurfaceControlViewHost h = new SurfaceControlViewHost(dc, d, (IBinder) null);
            h.setView(container, m.widthPixels, m.heightPixels);
            host = h;
        } else {
            ((SurfaceControlViewHost) host).relayout(m.widthPixels, m.heightPixels);
        }
    }

    private static void move(View v) {
        ViewParent p = v.getParent();
        if (p == container) return;
        if (p instanceof ViewGroup) ((ViewGroup) p).removeView(v);
        container.addView(v, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }
}
