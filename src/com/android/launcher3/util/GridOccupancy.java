package com.android.launcher3.util;

import android.graphics.Rect;
import com.android.launcher3.ItemInfo;

/**
 * Utility object to manage the occupancy in a grid.
 */
public class GridOccupancy {

public final boolean[][] cells;
private final int mCountX;
private final int mCountY;

public GridOccupancy(final int countX, final int countY) {
	mCountX = countX;
	mCountY = countY;
	cells = new boolean[countX][countY];
}

/**
 * Find the first vacant cell, if there is one.
 *
 * @param vacantOut Holds the x and y coordinate of the vacant cell
 * @param spanX     Horizontal cell span.
 * @param spanY     Vertical cell span.
 * @return true if a vacant cell was found
 */
public boolean findVacantCell(final int[] vacantOut, final int spanX,
                              final int spanY) {
	for (int y = 0; (y + spanY) <= mCountY; y++) {
		for (int x = 0; (x + spanX) <= mCountX; x++) {
			boolean available = !cells[x][y];
out:
			for (int i = x; i < x + spanX; i++) {
				for (int j = y; j < y + spanY; j++) {
					available = available && !cells[i][j];
					if (!available)
						break out;
				}
			}
			if (available) {
				vacantOut[0] = x;
				vacantOut[1] = y;
				return true;
			}
		}
	}
	return false;
}

public void copyTo(final GridOccupancy dest) {
	for (int i = 0; i < mCountX; i++) {
		for (int j = 0; j < mCountY; j++) {
			dest.cells[i][j] = cells[i][j];
		}
	}
}

public boolean isRegionVacant(final int x, final int y, final int spanX,
                              final int spanY) {
	int x2 = x + spanX - 1;
	int y2 = y + spanY - 1;
	if (x < 0 || y < 0 || x2 >= mCountX || y2 >= mCountY) {
		return false;
	}
	for (int i = x; i <= x2; i++) {
		for (int j = y; j <= y2; j++) {
			if (cells[i][j]) {
				return false;
			}
		}
	}
	return true;
}

public void markCells(final int cellX, final int cellY, final int spanX,
                      final int spanY, final boolean value) {
	if (cellX < 0 || cellY < 0)
		return;
	for (int x = cellX; x < cellX + spanX && x < mCountX; x++) {
		for (int y = cellY; y < cellY + spanY && y < mCountY; y++) {
			cells[x][y] = value;
		}
	}
}

public void markCells(final Rect r, final boolean value) {
	markCells(r.left, r.top, r.width(), r.height(), value);
}

public void markCells(final CellAndSpan cell, final boolean value) {
	markCells(cell.cellX, cell.cellY, cell.spanX, cell.spanY, value);
}

public void markCells(final ItemInfo item, final boolean value) {
	markCells(item.cellX, item.cellY, item.spanX, item.spanY, value);
}

public void clear() {
	markCells(0, 0, mCountX, mCountY, false);
}
}
