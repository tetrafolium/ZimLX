package com.google.android.apps.nexuslauncher;

import android.animation.AnimatorSet;

import androidx.annotation.Nullable;

import com.android.launcher3.Launcher;
import com.google.android.apps.nexuslauncher.smartspace.SmartspaceView;
import com.google.android.libraries.gsa.launcherclient.LauncherClient;

public class NexusLauncherActivity extends Launcher {
    private NexusLauncher mLauncher;

    public NexusLauncherActivity() {
        mLauncher = new NexusLauncher(this);
    }

    /* @Override
      public void onCreate(Bundle savedInstanceState) {
          super.onCreate(savedInstanceState);

          SharedPreferences prefs = Utilities.getPrefs(this);
          if (!FeedBridge.Companion.getInstance(this).isInstalled()) {
              prefs.edit().putBoolean(SettingsActivity.ENABLE_MINUS_ONE_PREF, false).apply();
          }

          super.onCreate(savedInstanceState);
      }
    */
    @Nullable
    public LauncherClient getGoogleNow() {
        return mLauncher.mClient;
    }

    public void playQsbAnimation() {
        mLauncher.mQsbAnimationController.dZ();
    }

    public AnimatorSet openQsb() {
        return mLauncher.mQsbAnimationController.openQsb();
    }

    public void registerSmartspaceView(SmartspaceView smartspace) {
        mLauncher.registerSmartspaceView(smartspace);
    }
}

