/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.launcher3.util.rule;

import android.app.Activity;
import android.app.Application;
import android.app.Application.ActivityLifecycleCallbacks;
import android.content.Intent;
import android.os.Bundle;

import com.android.launcher3.Launcher;
import com.android.launcher3.Workspace.ItemOperator;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.concurrent.Callable;

import androidx.test.InstrumentationRegistry;

/**
 * Test rule to get the current Launcher activity.
 */
public class LauncherActivityRule implements TestRule {

    private Launcher mActivity;

    public static Intent getHomeIntent() {
        return new Intent(Intent.ACTION_MAIN)
               .addCategory(Intent.CATEGORY_HOME)
               .setPackage(InstrumentationRegistry.getTargetContext().getPackageName())
               .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    @Override
    public Statement apply(final Statement base, final Description description) {
        return new MyStatement(base);
    }

    public Launcher getActivity() {
        return mActivity;
    }

    public Callable<Boolean> itemExists(final ItemOperator op) {
        return new Callable<Boolean>() {

            @Override
            public Boolean call() {
                Launcher launcher = getActivity();
                if (launcher == null) {
                    return false;
                }
                return launcher.getWorkspace().getFirstMatch(op) != null;
            }
        };
    }

    /**
     * Starts the launcher activity in the target package.
     */
    public void startLauncher() {
        InstrumentationRegistry.getInstrumentation().startActivitySync(getHomeIntent());
    }

    public void returnToHome() {
        InstrumentationRegistry.getTargetContext().startActivity(getHomeIntent());
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private class MyStatement extends Statement implements ActivityLifecycleCallbacks {

        private final Statement mBase;

        public MyStatement(final Statement base) {
            mBase = base;
        }

        @Override
        public void evaluate() {
            Application app = (Application)
                              InstrumentationRegistry.getTargetContext().getApplicationContext();
            app.registerActivityLifecycleCallbacks(this);
            try {
                mBase.evaluate();
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            } finally {
                app.unregisterActivityLifecycleCallbacks(this);
            }
        }

        @Override
        public void onActivityCreated(final Activity activity, final Bundle bundle) {
            if (activity instanceof Launcher) {
                mActivity = (Launcher) activity;
            }
        }

        @Override
        public void onActivityStarted(final Activity activity) {
        }

        @Override
        public void onActivityResumed(final Activity activity) {
        }

        @Override
        public void onActivityPaused(final Activity activity) {
        }

        @Override
        public void onActivityStopped(final Activity activity) {
        }

        @Override
        public void onActivitySaveInstanceState(final Activity activity, final Bundle bundle) {
        }

        @Override
        public void onActivityDestroyed(final Activity activity) {
            if (activity == mActivity) {
                mActivity = null;
            }
        }
    }
}
