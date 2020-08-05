package com.google.android.apps.nexuslauncher;

import android.content.ComponentName;
import android.content.Context;
import android.os.UserHandle;

import com.android.launcher3.Utilities;
import com.android.launcher3.util.ComponentKey;

import org.zimmob.zimlx.ZimAppFilter;

import java.util.HashSet;
import java.util.Set;

public class CustomAppFilter extends ZimAppFilter {
    private final Context mContext;

    public CustomAppFilter(final Context context) {
        super(context);
        mContext = context;
    }

    static void setComponentNameState(final Context context, final ComponentKey key, final boolean hidden) {
        String comp = key.toString();
        Set<String> hiddenApps = new HashSet<>(getHiddenApps(context));
        while (hiddenApps.contains(comp)) {
            hiddenApps.remove(comp);
        }
        if (hidden) {
            hiddenApps.add(comp);
        }
        setHiddenApps(context, hiddenApps);
    }

    static boolean isHiddenApp(final Context context, final ComponentKey key) {
        return getHiddenApps(context).contains(key.toString());
    }

    // This can't be null anyway
    public static Set<String> getHiddenApps(final Context context) {
        return new HashSet<>(Utilities.getZimPrefs(context).getHiddenAppSet());
    }

    public static void setHiddenApps(final Context context, final Set<String> hiddenApps) {
        Utilities.getZimPrefs(context).setHiddenAppSet(hiddenApps);
    }

    @Override
    public boolean shouldShowApp(final ComponentName componentName, final UserHandle user) {
        return super.shouldShowApp(componentName, user)
               && (user == null || !isHiddenApp(mContext, new ComponentKey(componentName, user)));
    }
}
