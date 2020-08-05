package com.google.android.libraries.gsa.launcherclient;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;
import org.zimmob.zimlx.smartspace.FeedBridge;

public class BaseClientService implements ServiceConnection {
  private boolean mConnected;
  private final Context mContext;
  private final int mFlags;
  private final ServiceConnection mBridge;

  BaseClientService(final Context context, final int flags) {
    mContext = context;
    mFlags = flags;
    mBridge = FeedBridge.getUseBridge() ? new LauncherClientBridge(this, flags)
                                        : this;
  }

  public final boolean connect() {
    if (!mConnected) {
      try {
        mConnected = mContext.bindService(
            LauncherClient.getIntent(mContext, FeedBridge.getUseBridge()),
            mBridge, mFlags);
      } catch (Throwable e) {
        Log.e("LauncherClient", "Unable to connect to overlay service", e);
      }
    }
    return mConnected;
  }

  public final void disconnect() {
    if (mConnected) {
      mContext.unbindService(mBridge);
      mConnected = false;
    }
  }

  @Override
  public void onServiceConnected(final ComponentName name,
                                 final IBinder service) {}

  @Override
  public void onServiceDisconnected(final ComponentName name) {}
}
