package com.android.launcher3;

import android.app.backup.BackupAgent;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.os.ParcelFileDescriptor;
import com.android.launcher3.logging.FileLog;
import com.android.launcher3.provider.RestoreDbTask;

public class LauncherBackupAgent extends BackupAgent {

  @Override
  public void onCreate() {
    super.onCreate();
    // Set the log dir as LauncherAppState is not initialized during restore.
    FileLog.setDir(getFilesDir());
  }

  @Override
  public void onRestore(final BackupDataInput data, final int appVersionCode,
                        final ParcelFileDescriptor newState) {
    // Doesn't do incremental backup/restore
  }

  @Override
  public void onBackup(final ParcelFileDescriptor oldState,
                       final BackupDataOutput data,
                       final ParcelFileDescriptor newState) {
    // Doesn't do incremental backup/restore
  }

  @Override
  public void onRestoreFinished() {
    RestoreDbTask.setPending(this, true);
  }
}
