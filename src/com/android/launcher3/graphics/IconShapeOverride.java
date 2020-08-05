/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.graphics;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.LooperExecutor;

import org.zimmob.zimlx.ZimLauncher;

import java.lang.reflect.Field;
import java.util.Arrays;

import static com.android.launcher3.Utilities.getDevicePrefs;
import static com.android.launcher3.Utilities.getPrefs;
import static org.zimmob.zimlx.util.ZimFlags.THEME_ICON_SHAPE;

/**
 * Utility class to override shape of {@link android.graphics.drawable.AdaptiveIconDrawable}.
 */
@TargetApi(Build.VERSION_CODES.O)
public class IconShapeOverride {

    private static final String TAG = "IconShapeOverride";

    // Time to wait before killing the process this ensures that the progress bar is visible for
    // sufficient time so that there is no flicker.
    private static final long PROCESS_KILL_DELAY_MS = 1000;

    private static final int RESTART_REQUEST_CODE = 42; // the answer to everything

    public static boolean isSupported(Context context) {
        if (!Utilities.ATLEAST_OREO) {
            return false;
        }

        try {
            if (getSystemResField().get(null) != Resources.getSystem()) {
                // Our assumption that mSystem is the system resource is not true.
                return false;
            }
        } catch (Exception e) {
            // Ignore, not supported
            return false;
        }

        return getConfigResId() != 0;
    }

    public static void apply(Context context) {
        if (!Utilities.ATLEAST_OREO) {
            return;
        }
        String path = getAppliedValue(context);
        if (TextUtils.isEmpty(path)) {
            return;
        }
        if (!isSupported(context)) {
            return;
        }

        // magic
        try {
            Resources override =
                new ResourcesOverride(Resources.getSystem(), getConfigResId(), path);
            getSystemResField().set(null, override);
            int masks = getOverrideMasksResId();
            if (masks != 0) {
                ((ResourcesOverride) override).setArrayOverrideId(masks);
            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to override icon shape", e);
            // revert value.
            getDevicePrefs(context).edit().remove(THEME_ICON_SHAPE).apply();
        }
    }

    private static Field getSystemResField() throws Exception {
        Field staticField = Resources.class.getDeclaredField("mSystem");
        staticField.setAccessible(true);
        return staticField;
    }

    private static int getConfigResId() {
        return Resources.getSystem().getIdentifier("config_icon_mask", "string", "android");
    }

    private static int getOverrideMasksResId() {
        return Resources.getSystem().getIdentifier("system_icon_masks", "array", "android");
    }

    public static String getAppliedValue(Context context) {
        String devValue = getDevicePrefs(context).getString(THEME_ICON_SHAPE, "");
        if (!TextUtils.isEmpty(devValue)) {
            // Migrate to general preferences to back up shape overrides
            getPrefs(context).edit().putString(THEME_ICON_SHAPE, devValue).apply();
            getDevicePrefs(context).edit().remove(THEME_ICON_SHAPE).apply();
        }

        return getPrefs(context).getString(THEME_ICON_SHAPE, "");
    }

    public static void handlePreferenceUi(ListPreference preference) {
        Context context = preference.getContext();
        preference.setValue(getAppliedValue(context));
        preference.setOnPreferenceChangeListener(new PreferenceChangeHandler(context));
    }

    private static class ResourcesOverride extends Resources {

        private final int mOverrideId;
        private int mArrayOverrideId = 0;
        private final String mOverrideValue;

        public ResourcesOverride(Resources parent, int overrideId, String overrideValue) {
            super(parent.getAssets(), parent.getDisplayMetrics(), parent.getConfiguration());
            mOverrideId = overrideId;
            mOverrideValue = overrideValue;
        }

        @NonNull
        @Override
        public String getString(int id) throws NotFoundException {
            if (id == mOverrideId) {
                return mOverrideValue;
            }
            return super.getString(id);
        }

        void setArrayOverrideId(int id) {
            mArrayOverrideId = id;
        }

        // I do admit that this is one hell of a hack
        @NonNull
        @Override
        public String[] getStringArray(int id) throws NotFoundException {
            if (id != 0 && id == mArrayOverrideId) {
                int size = super.getStringArray(id).length;
                String[] arr = new String[size];
                Arrays.fill(arr, mOverrideValue);
                return arr;
            }
            return super.getStringArray(id);
        }
    }

    private static class PreferenceChangeHandler implements Preference.OnPreferenceChangeListener {

        private final Context mContext;

        private PreferenceChangeHandler(Context context) {
            mContext = context;
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object o) {
            String newValue = (String) o;
            if (!getAppliedValue(mContext).equals(newValue)) {
                if (preference instanceof ListPreference) {
                    ((ListPreference) preference).setValue(newValue);
                }

                new LooperExecutor(LauncherModel.getWorkerLooper()).execute(
                    new OverrideApplyHandler(mContext, newValue));
            }
            return false;
        }
    }

    private static class OverrideApplyHandler implements Runnable {

        private final Context mContext;
        private final String mValue;

        private OverrideApplyHandler(Context context, String value) {
            mContext = context;
            mValue = value;
        }

        @SuppressLint("ApplySharedPref")
        @Override
        public void run() {
            // Synchronously write the preference.
            getDevicePrefs(mContext).edit().putString(THEME_ICON_SHAPE, mValue).commit();
            // Clear the icon cache.
            LauncherAppState.getInstance(mContext).getIconCache().clear();

            // Schedule restart
            ((ZimLauncher) LauncherAppState.getInstanceNoCreate().getLauncher())
            .scheduleRestart();
        }
    }
}
