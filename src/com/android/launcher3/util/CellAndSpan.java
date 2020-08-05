package com.android.launcher3.util;

/**
 * Base class which represents an area on the grid.
 */
public class CellAndSpan {

  /**
   * Indicates the X position of the associated cell.
   */
  public int cellX = -1;

  /**
   * Indicates the Y position of the associated cell.
   */
  public int cellY = -1;

  /**
   * Indicates the X cell span.
   */
  public int spanX = 1;

  /**
   * Indicates the Y cell span.
   */
  public int spanY = 1;

  public CellAndSpan() {}

  public CellAndSpan(final int cellX, final int cellY, final int spanX,
                     final int spanY) {
    this.cellX = cellX;
    this.cellY = cellY;
    this.spanX = spanX;
    this.spanY = spanY;
  }

  public void copyFrom(final CellAndSpan copy) {
    cellX = copy.cellX;
    cellY = copy.cellY;
    spanX = copy.spanX;
    spanY = copy.spanY;
  }

  public String toString() {
    return "(" + cellX + ", " + cellY + ": " + spanX + ", " + spanY + ")";
  }
}
