package org.zimmob.zimlx.model;

public class AppCountInfo {
  private String packageName;
  private int count;

  public AppCountInfo(final String name, final int count) {
    this.packageName = name;
    this.count = count;
  }

  public String getPackageName() { return packageName; }

  public void setPackageName(final String packageName) {
    this.packageName = packageName;
  }

  public int getCount() { return count; }

  public void setCount(final int count) { this.count = count; }
}
