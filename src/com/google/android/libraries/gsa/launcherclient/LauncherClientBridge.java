package com.google.android.libraries.gsa.launcherclient;

import amirz.aidlbridge.IBridge;
import amirz.aidlbridge.IBridgeCallback;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;

public class LauncherClientBridge
    extends IBridgeCallback.Stub implements ServiceConnection {
  private static final String INTERFACE_DESCRIPTOR = "amirz.aidlbridge.IBridge";

  private final BaseClientService mClientService;
  private final int mFlags;
  private ComponentName mConnectionName;

  public LauncherClientBridge(final BaseClientService launcherClientService,
                              final int flags) {
    mClientService = launcherClientService;
    mFlags = flags;
  }

  @Override
  public void onServiceConnected(final ComponentName name,
                                 final IBinder service) {
    try {
      if (INTERFACE_DESCRIPTOR.equals(service.getInterfaceDescriptor())) {
        IBridge bridge = IBridge.Stub.asInterface(service);
        try {
          bridge.bindService(this, mFlags);
        } catch (RemoteException e) {
          e.printStackTrace();
        }
      } else {
        mClientService.onServiceConnected(name, service);
        mConnectionName = name;
      }
    } catch (RemoteException e) {
      e.printStackTrace();
    }
  }
  @Override
  public void onServiceDisconnected(final ComponentName name) {
    if (mConnectionName != null) {
      mClientService.onServiceDisconnected(mConnectionName);
      mConnectionName = null;
    }
  }
}
