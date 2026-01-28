package com.getcapacitor.community.safearea;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Color;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.Locale;

@CapacitorPlugin(name = "SafeArea")
public class SafeAreaPlugin extends Plugin {
    private FrameLayout contentFrameLayout;

    // App theme colors
    private static final int COLOR_DARK = Color.parseColor("#212b35");
    private static final int COLOR_LIGHT = Color.parseColor("#ffffff");

    protected boolean hasMetaViewportCover = true;

    // Use an initial value of `null`, so this plugin doesn't override any existing behavior by default
    private SystemBarsStyle statusBarStyle = null;

    // Use an initial value of `null`, so this plugin doesn't override any existing behavior by default
    private SystemBarsStyle navigationBarStyle = null;

    // Declare variable at this scope to help prevent adding multiple listeners.
    private SafeAreaWebViewListener webViewListener;

    @Override
    public void load() {
        super.load();

        warnAboutUnsupportedConfigurationValues();

        String statusBarStyleString = getConfig().getConfigJSON().optString("statusBarStyle");
        if (!statusBarStyleString.isBlank()) {
            statusBarStyle = getSystemBarsStyleFromString(statusBarStyleString);
        }

        String navigationBarStyleString = getConfig().getConfigJSON().optString("navigationBarStyle");
        if (!navigationBarStyleString.isBlank()) {
            navigationBarStyle = getSystemBarsStyleFromString(navigationBarStyleString);
        }

        updateSystemBarsStyle();

        hasMetaViewportCover = getConfig().getConfigJSON().optBoolean("initialViewportFitCover", true);

        setupSafeAreaInsets();
    }

    @Override
    public void handleOnStart() {
        super.handleOnStart();

        boolean detectViewportFitCoverChanges = getConfig().getConfigJSON().optBoolean("detectViewportFitCoverChanges", true);

        if (detectViewportFitCoverChanges) {
            if (webViewListener == null) {
                webViewListener = new SafeAreaWebViewListener(getBridge());
                getBridge().addWebViewListener(webViewListener);
            }
        }
    }

    private void warnAboutUnsupportedConfigurationValues() {
        String systemBarsInsetsHandling = bridge.getConfig().getPluginConfiguration("SystemBars").getConfigJSON().optString("insetsHandling");
        if (!systemBarsInsetsHandling.equals("disable")) {
            Log.e("SafeAreaPlugin", "You should set `SystemBars.insetsHandling` to `disable` in your `capacitor.config.json`. Other values can lead to unexpected behavior.");
        }
    }

    private void setupSafeAreaInsets() {
        // Use content FrameLayout instead of decorView to avoid conflicting with Keyboard plugin
        FrameLayout content = getActivity().getWindow().getDecorView().findViewById(android.R.id.content);
        contentFrameLayout = content; // Store reference for theme changes

        // Set the content background based on theme so padded areas look correct
        updateContentBackgroundForTheme(content);

        // Apply listener to the content FrameLayout, not decorView
        // This way Keyboard plugin's listener on decorView/rootView still works
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, windowInsets) -> {
            Insets systemBarsInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());

            // Update background color for current theme
            updateContentBackgroundForTheme(content);

            // Apply padding for status bar only (top)
            // Bottom safe area is handled by the web app via CSS env(safe-area-inset-bottom)
            // Keyboard resize is handled by Keyboard plugin
            v.setPadding(0, systemBarsInsets.top, 0, 0);

            // Return insets with system bars consumed, but leave IME insets for Keyboard plugin
            return new WindowInsetsCompat.Builder(windowInsets)
                    .setInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout(),
                            Insets.of(0, 0, 0, 0))
                    .build();
        });
    }

    private void updateContentBackgroundForTheme(View content) {
        // Use the user-requested style if set, otherwise fall back to device theme
        SystemBarsStyle style = statusBarStyle != null ? statusBarStyle : getStyleForTheme(getActivity());
        if (style == SystemBarsStyle.DEFAULT) {
            style = getStyleForTheme(getActivity());
        }
        if (style == SystemBarsStyle.DARK) {
            content.setBackgroundColor(COLOR_DARK);
        } else {
            content.setBackgroundColor(COLOR_LIGHT);
        }
    }

    public enum SystemBarsStyle {
        DARK("DARK"),
        LIGHT("LIGHT"),
        DEFAULT("DEFAULT");

        public final String value;

        SystemBarsStyle(String value) {
            this.value = value;
        }
    }

    private @NonNull SystemBarsStyle getSystemBarsStyleFromString(@Nullable String value) {
        if (value != null) {
            try {
                return SystemBarsStyle.valueOf(value.toUpperCase(Locale.US));
            } catch (IllegalArgumentException error) {
                // invalid value
            }
        }

        return SystemBarsStyle.DEFAULT;
    }

    public enum SystemBarsType {
        STATUS_BAR("STATUS_BAR"),
        NAVIGATION_BAR("NAVIGATION_BAR");

        public final String value;

        SystemBarsType(String value) {
            this.value = value;
        }
    }

    private @Nullable SystemBarsType getSystemBarsTypeFromString(@Nullable String value) {
        if (value != null) {
            try {
                return SystemBarsType.valueOf(value.toUpperCase(Locale.US));
            } catch (IllegalArgumentException error) {
                // invalid value
            }
        }

        return null;
    }

    @PluginMethod(returnType = PluginMethod.RETURN_NONE)
    public void setSystemBarsStyle(final PluginCall call) {
        String style = call.getString("style");
        String type = call.getString("type");

        SystemBarsStyle systemBarsStyle = getSystemBarsStyleFromString(style);
        SystemBarsType systemBarsType = getSystemBarsTypeFromString(type);

        if (systemBarsType == null || systemBarsType == SystemBarsType.STATUS_BAR) {
            statusBarStyle = systemBarsStyle;
        }

        if (systemBarsType == null || systemBarsType == SystemBarsType.NAVIGATION_BAR) {
            navigationBarStyle = systemBarsStyle;
        }

        getBridge().executeOnMainThread(() -> {
            updateSystemBarsStyle();
            call.resolve();
        });
    }

    @Override
    protected void handleOnConfigurationChanged(Configuration newConfig) {
        super.handleOnConfigurationChanged(newConfig);
        getBridge().executeOnMainThread(() -> {
            updateSystemBarsStyle();
            // Update content background for theme change
            if (contentFrameLayout != null) {
                updateContentBackgroundForTheme(contentFrameLayout);
            }
        });
    }

    private void updateSystemBarsStyle() {
        if (statusBarStyle != null) {
            setSystemBarsStyle(getActivity(), statusBarStyle, SystemBarsType.STATUS_BAR);
        }
        if (navigationBarStyle != null) {
            setSystemBarsStyle(getActivity(), navigationBarStyle, SystemBarsType.NAVIGATION_BAR);
        }
    }

    public static void setSystemBarsStyle(Activity activity, SystemBarsStyle style) {
        setSystemBarsStyle(activity, style, null);
    }

    public static void setSystemBarsStyle(Activity activity, SystemBarsStyle style, @Nullable SystemBarsType type) {
        if (style == SystemBarsStyle.DEFAULT) {
            style = getStyleForTheme(activity);
        }

        Window window = activity.getWindow();
        WindowInsetsControllerCompat windowInsetsControllerCompat = WindowCompat.getInsetsController(window, window.getDecorView());

        // Ensure we can draw system bar backgrounds
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);

        if (type == null || type == SystemBarsType.STATUS_BAR) {
            windowInsetsControllerCompat.setAppearanceLightStatusBars(style != SystemBarsStyle.DARK);
        }

        if (type == null || type == SystemBarsType.NAVIGATION_BAR) {
            windowInsetsControllerCompat.setAppearanceLightNavigationBars(style != SystemBarsStyle.DARK);
        }

        // Set background color on both decorView and content FrameLayout
        // Content FrameLayout is where padding is applied, so it needs the correct background
        FrameLayout content = window.getDecorView().findViewById(android.R.id.content);
        if (style == SystemBarsStyle.DARK) {
            window.getDecorView().setBackgroundColor(COLOR_DARK);
            if (content != null) {
                content.setBackgroundColor(COLOR_DARK);
            }

            // Explicitly set status bar color to match
            if (type == null || type == SystemBarsType.STATUS_BAR) {
                window.setStatusBarColor(COLOR_DARK);
            }
        } else {
            window.getDecorView().setBackgroundColor(COLOR_LIGHT);
            if (content != null) {
                content.setBackgroundColor(COLOR_LIGHT);
            }

            // Explicitly set status bar color to match
            if (type == null || type == SystemBarsType.STATUS_BAR) {
                window.setStatusBarColor(COLOR_LIGHT);
            }
        }
    }

    private static SystemBarsStyle getStyleForTheme(Activity activity) {
        int currentNightMode = activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (currentNightMode != Configuration.UI_MODE_NIGHT_YES) {
            return SystemBarsStyle.LIGHT;
        }
        return SystemBarsStyle.DARK;
    }

    @PluginMethod(returnType = PluginMethod.RETURN_NONE)
    public void showSystemBars(final PluginCall call) {
        String type = call.getString("type");
        SystemBarsType systemBarsType = getSystemBarsTypeFromString(type);

        getBridge().executeOnMainThread(() -> {
            setSystemBarsHidden(false, systemBarsType);
            call.resolve();
        });
    }

    @PluginMethod(returnType = PluginMethod.RETURN_NONE)
    public void hideSystemBars(final PluginCall call) {
        String type = call.getString("type");
        SystemBarsType systemBarsType = getSystemBarsTypeFromString(type);

        getBridge().executeOnMainThread(() -> {
            setSystemBarsHidden(true, systemBarsType);
            call.resolve();
        });
    }

    private void setSystemBarsHidden(Boolean hidden, @Nullable SystemBarsType type) {
        Window window = getActivity().getWindow();
        WindowInsetsControllerCompat windowInsetsControllerCompat = WindowCompat.getInsetsController(window, window.getDecorView());

        if (hidden) {
            if (type == null || type == SystemBarsType.STATUS_BAR) {
                windowInsetsControllerCompat.hide(WindowInsetsCompat.Type.statusBars());
            }
            if (type == null || type == SystemBarsType.NAVIGATION_BAR) {
                windowInsetsControllerCompat.hide(WindowInsetsCompat.Type.navigationBars());
            }
            return;
        }

        if (type == null || type == SystemBarsType.STATUS_BAR) {
            windowInsetsControllerCompat.show(WindowInsetsCompat.Type.systemBars());
        }
        if (type == null || type == SystemBarsType.NAVIGATION_BAR) {
            windowInsetsControllerCompat.show(WindowInsetsCompat.Type.navigationBars());
        }
    }
}
