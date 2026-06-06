package io.github.adblock.tg;

import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Read-only reconnaissance: it logs, it does not block. */
class AdDiagnostics {

    private final XposedModule x;   // call hook()/log() through the module instance
    private final String tag;

    AdDiagnostics(XposedModule module, String tag) {
        this.x = module;
        this.tag = tag;
    }

    // Keywords that usually mark ad / sponsored surfaces in Telegram source.
    private static final String[] NAME_HINTS = {
        "sponsor", "sponsored", "ad", "ads", "advert", "promo", "promoted",
        "monetiz", "reveue", "revenue"
    };

    // Classes worth inspecting directly (extend as you reverse-engineer).
    private static final String[] CANDIDATE_CLASSES = {
        "org.telegram.messenger.MessageObject",
        "org.telegram.messenger.MessagesController",
        "org.telegram.ui.bots.BotAdView",
        "org.telegram.ui.ChatActivity",
        "org.telegram.ui.Cells.ChatMessageCell",
        "org.telegram.ui.Cells.DialogCell",
        "org.telegram.ui.Adapters.DialogsAdapter",
        "org.telegram.ui.Stories.StoriesController",
    };

    void run(ClassLoader cl) {
        log("=== Telegram ad diagnostics start ===");
        probeCandidateClasses(cl);
        installImpressionTracers(cl);
        log("=== Telegram ad diagnostics end ===");
    }

    /* (A) Reflectively dump ad-ish members of known classes. */
    private void probeCandidateClasses(ClassLoader cl) {
        for (String name : CANDIDATE_CLASSES) {
            try {
                Class<?> c = cl.loadClass(name);
                for (Method m : c.getDeclaredMethods()) {
                    if (matches(m.getName())) {
                        log("METHOD  " + c.getSimpleName() + "#" + m.getName()
                                + sig(m) + " -> " + m.getReturnType().getSimpleName());
                    }
                }
                for (Field f : c.getDeclaredFields()) {
                    if (matches(f.getName())) {
                        log("FIELD   " + c.getSimpleName() + "#" + f.getName()
                                + " : " + f.getType().getSimpleName());
                    }
                }
            } catch (Throwable ignore) {
                // class not present in this build/version — fine.
            }
        }
    }

    /* (B) Hook impression/click reporters so we see *where* ads are shown. */
    private void installImpressionTracers(ClassLoader cl) {
        traceMethodsNamed(cl, "org.telegram.ui.ChatActivity", "logSponsoredClicked");
        traceMethodsNamed(cl, "org.telegram.messenger.MessagesController", "markSponsoredAsRead");
        traceMethodsNamed(cl, "org.telegram.messenger.MessagesController", "getSponsoredMessages");
        // BotAdView constructor: anything that builds an ad card.
        traceConstructors(cl, "org.telegram.ui.bots.BotAdView");
    }

    private void traceMethodsNamed(ClassLoader cl, String cls, String method) {
        try {
            Class<?> c = cl.loadClass(cls);
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals(method)) continue;
                x.hook(m)
                 .setPriority(XposedInterface.PRIORITY_LOWEST)
                 .intercept(chain -> {
                     log("CALLED " + cls + "#" + method
                             + "\n" + stack());
                     return chain.proceed();   // observe only, do not block
                 });
                log("Tracing " + cls + "#" + method);
            }
        } catch (Throwable ignore) { }
    }

    private void traceConstructors(ClassLoader cl, String cls) {
        try {
            Class<?> c = cl.loadClass(cls);
            for (java.lang.reflect.Constructor<?> ctor : c.getDeclaredConstructors()) {
                x.hook(ctor)
                 .setPriority(XposedInterface.PRIORITY_LOWEST)
                 .intercept(chain -> {
                     log("NEW " + cls + "\n" + stack());
                     return chain.proceed();
                 });
            }
            log("Tracing constructors of " + cls);
        } catch (Throwable ignore) { }
    }

    /* helpers */
    private static boolean matches(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        for (String h : NAME_HINTS) {
            // word-ish match to avoid matching "thread", "added", "head"...
            if (n.equals(h) || n.startsWith(h) || n.contains(h.length() > 2 ? h : (h + "_"))) {
                if (h.length() <= 2) {            // "ad"/"ads": require boundary
                    if (n.equals("ad") || n.equals("ads")
                            || n.startsWith("ad_") || n.endsWith("ad")
                            || n.contains("sponsoredad")) return true;
                } else {
                    return true;
                }
            }
        }
        return false;
    }

    private static String sig(Method m) {
        StringBuilder b = new StringBuilder("(");
        Class<?>[] p = m.getParameterTypes();
        for (int i = 0; i < p.length; i++) {
            if (i > 0) b.append(", ");
            b.append(p[i].getSimpleName());
        }
        return b.append(")").toString();
    }

    private static String stack() {
        StringBuilder b = new StringBuilder();
        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        // skip the first few VM/hook frames
        for (int i = 3; i < Math.min(st.length, 18); i++) {
            b.append("    at ").append(st[i]).append('\n');
        }
        return b.toString();
    }

    private void log(String msg) {
        x.log(Log.INFO, tag, msg);
    }
}
