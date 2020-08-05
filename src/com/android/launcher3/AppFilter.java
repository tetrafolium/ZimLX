package com.android.launcher3;

import android.content.ComponentName;
import android.content.Context;
import android.os.UserHandle;

public class AppFilter {

    public static AppFilter newInstance(final Context context) {
        return Utilities.getOverrideObject(AppFilter.class, context, R.string.app_filter_class);
    }

    public boolean shouldShowApp(final ComponentName app) {
        return true;
    }

    public boolean shouldShowApp(final ComponentName app, final UserHandle user) {
        return true;
    }
}
