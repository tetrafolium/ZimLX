/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.launcher3.allapps;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Process;
import android.os.UserHandle;
import android.util.AttributeSet;
import android.widget.Switch;

import com.android.launcher3.compat.UserManagerCompat;

import java.util.List;

public class WorkModeSwitch extends Switch {

    public WorkModeSwitch(final Context context) {
        super(context);
    }

    public WorkModeSwitch(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    public WorkModeSwitch(final Context context, final AttributeSet attrs, final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setChecked(final boolean checked) {
        // No-op, do not change the checked state until broadcast is received.
    }

    @Override
    public void toggle() {
        trySetQuietModeEnabledToAllProfilesAsync(isChecked());
    }

    private void setCheckedInternal(final boolean checked) {
        super.setChecked(checked);
    }

    public void refresh() {
        UserManagerCompat userManager = UserManagerCompat.getInstance(getContext());
        setCheckedInternal(!userManager.isAnyProfileQuietModeEnabled());
        setEnabled(true);
    }

    private void trySetQuietModeEnabledToAllProfilesAsync(final boolean enabled) {
        new AsyncTask<Void, Void, Boolean>() {

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                setEnabled(false);
            }

            @Override
            protected Boolean doInBackground(final Void... voids) {
                UserManagerCompat userManager = UserManagerCompat.getInstance(getContext());
                List<UserHandle> userProfiles = userManager.getUserProfiles();
                boolean showConfirm = false;
                for (UserHandle userProfile : userProfiles) {
                    if (Process.myUserHandle().equals(userProfile)) {
                        continue;
                    }
                    showConfirm |= !userManager.requestQuietModeEnabled(enabled, userProfile);
                }
                return showConfirm;
            }

            @Override
            protected void onPostExecute(final Boolean showConfirm) {
                if (showConfirm) {
                    setEnabled(true);
                }
            }
        } .execute();
    }
}
