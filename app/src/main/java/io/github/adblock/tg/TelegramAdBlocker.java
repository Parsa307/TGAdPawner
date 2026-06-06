package io.github.adblock.tg;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;

public class TelegramAdBlocker extends XposedModule {

    private static final String TAG = "TGAdBlock";

    // libxposed API 101: no-arg constructor; framework attaches the interface.
    public TelegramAdBlocker() { }

    @Override
    public void onModuleLoaded(@NonNull ModuleLoadedParam param) {
        // Runs in the module's own process. Nothing needed here.
    }

    @Override
    public void onPackageLoaded(@NonNull PackageLoadedParam param) {
        // Only act on the first (main) classloader of a Telegram package.
        if (!param.getPackageName().startsWith("org.telegram")
                && !param.getPackageName().equals("org.telegram.plus")
                && !param.getPackageName().equals("nekox.messenger")) {
            return;
        }
        if (!param.isFirstPackage()) return;

        ClassLoader cl = param.getDefaultClassLoader();
        log(Log.INFO, TAG, "Hooking " + param.getPackageName());

        hookIsSponsored(cl);
        hookGetSponsoredMessages(cl);
        hookBotAdView(cl);

        // Comment this out in a release build — it's noisy.
        new AdDiagnostics(this, TAG).run(cl);
    }

    /* ---------- Layer 1: pretend nothing is sponsored ---------- */
    private void hookIsSponsored(ClassLoader cl) {
        try {
            Class<?> mo = cl.loadClass("org.telegram.messenger.MessageObject");
            Method m = mo.getDeclaredMethod("isSponsored");
            hook(m)
                .setPriority(XposedInterface.PRIORITY_HIGHEST)
                .intercept(chain -> Boolean.FALSE);   // skip original, force false
            log(Log.INFO, TAG, "Hooked MessageObject.isSponsored");
        } catch (Throwable t) {
            log(Log.WARN, TAG, "isSponsored hook failed", t);
        }
    }

    /* ---------- Layer 2: empty the sponsored fetch ---------- */
    private void hookGetSponsoredMessages(ClassLoader cl) {
        try {
            Class<?> mc = cl.loadClass("org.telegram.messenger.MessagesController");
            for (Method m : mc.getDeclaredMethods()) {
                if (m.getName().equals("getSponsoredMessages")) {
                    hook(m)
                        .setPriority(XposedInterface.PRIORITY_HIGHEST)
                        .intercept(chain -> null);   // no sponsored messages available
                    log(Log.INFO, TAG,
                            "Hooked getSponsoredMessages " + m);
                }
            }
        } catch (Throwable t) {
            log(Log.WARN, TAG, "getSponsoredMessages hook failed", t);
        }
    }

    /* ---------- Layer 3: collapse the BotAdView card ---------- */
    private void hookBotAdView(ClassLoader cl) {
        try {
            Class<?> botAd = cl.loadClass("org.telegram.ui.bots.BotAdView");
            for (Method m : botAd.getDeclaredMethods()) {
                if (m.getName().equals("set")) {
                    hook(m).intercept(chain -> {
                        Object self = chain.getThisObject();
                        if (self instanceof View) {
                            View v = (View) self;
                            v.post(() -> {
                                v.setVisibility(View.GONE);
                                ViewGroup.LayoutParams lp = v.getLayoutParams();
                                if (lp != null) { lp.height = 0; v.setLayoutParams(lp); }
                            });
                        }
                        return null;   // never bind the ad content / never log the impression
                    });
                    log(Log.INFO, TAG, "Hooked BotAdView.set");
                }
            }
        } catch (Throwable t) {
            log(Log.WARN, TAG, "BotAdView hook failed", t);
        }
    }
}
