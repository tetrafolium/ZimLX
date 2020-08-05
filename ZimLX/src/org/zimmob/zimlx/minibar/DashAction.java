package org.zimmob.zimlx.minibar;

import android.content.Intent;

public class DashAction {

  public Intent extraData;
  public Action action;

  public DashAction(final Action action, final Intent extraData) {
    this.action = action;
    this.extraData = extraData;
  }

  public enum Action {
    EditMinibar,
    SetWallpaper,
    DeviceSettings,
    LauncherSettings,
    VolumeDialog,
    AppDrawer,
    MobileNetworkSettings,
    AppSettings,
    APP
  }
}
