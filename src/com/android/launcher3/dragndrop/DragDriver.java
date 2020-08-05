/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.launcher3.dragndrop;

import android.content.Context;
import android.view.DragEvent;
import android.view.MotionEvent;

import com.android.launcher3.DropTarget.DragObject;
import com.android.launcher3.Utilities;

/**
 * Base class for driving a drag/drop operation.
 */
public abstract class DragDriver {
    protected final EventListener mEventListener;

    public DragDriver(final EventListener eventListener) {
        mEventListener = eventListener;
    }

    public static DragDriver create(final Context context, final DragController dragController,
                                    final DragObject dragObject, final DragOptions options) {
        if (Utilities.ATLEAST_NOUGAT && options.systemDndStartPoint != null) {
            return new SystemDragDriver(dragController, context, dragObject);
        } else {
            return new InternalDragDriver(dragController);
        }
    }

    /**
     * Handles ending of the DragView animation.
     */
    public void onDragViewAnimationEnd() {
    }

    public boolean onTouchEvent(final MotionEvent ev) {
        final int action = ev.getAction();

        switch (action) {
        case MotionEvent.ACTION_MOVE:
            mEventListener.onDriverDragMove(ev.getX(), ev.getY());
            break;
        case MotionEvent.ACTION_UP:
            mEventListener.onDriverDragMove(ev.getX(), ev.getY());
            mEventListener.onDriverDragEnd(ev.getX(), ev.getY());
            break;
        case MotionEvent.ACTION_CANCEL:
            mEventListener.onDriverDragCancel();
            break;
        }

        return true;
    }

    public abstract boolean onDragEvent(DragEvent event);


    public boolean onInterceptTouchEvent(final MotionEvent ev) {
        final int action = ev.getAction();

        switch (action) {
        case MotionEvent.ACTION_UP:
            mEventListener.onDriverDragEnd(ev.getX(), ev.getY());
            break;
        case MotionEvent.ACTION_CANCEL:
            mEventListener.onDriverDragCancel();
            break;
        }

        return true;
    }

    public interface EventListener {
        void onDriverDragMove(float x, float y);

        void onDriverDragExitWindow();

        void onDriverDragEnd(float x, float y);

        void onDriverDragCancel();
    }
}

/**
 * Class for driving a system (i.e. framework) drag/drop operation.
 */
class SystemDragDriver extends DragDriver {

    float mLastX = 0;
    float mLastY = 0;

    SystemDragDriver(final DragController dragController, final Context context, final DragObject dragObject) {
        super(dragController);
    }

    @Override
    public boolean onTouchEvent(final MotionEvent ev) {
        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(final MotionEvent ev) {
        return false;
    }

    @Override
    public boolean onDragEvent(final DragEvent event) {
        final int action = event.getAction();

        switch (action) {
        case DragEvent.ACTION_DRAG_STARTED:
            mLastX = event.getX();
            mLastY = event.getY();
            return true;

        case DragEvent.ACTION_DRAG_ENTERED:
            return true;

        case DragEvent.ACTION_DRAG_LOCATION:
            mLastX = event.getX();
            mLastY = event.getY();
            mEventListener.onDriverDragMove(event.getX(), event.getY());
            return true;

        case DragEvent.ACTION_DROP:
            mLastX = event.getX();
            mLastY = event.getY();
            mEventListener.onDriverDragMove(event.getX(), event.getY());
            mEventListener.onDriverDragEnd(mLastX, mLastY);
            return true;
        case DragEvent.ACTION_DRAG_EXITED:
            mEventListener.onDriverDragExitWindow();
            return true;

        case DragEvent.ACTION_DRAG_ENDED:
            mEventListener.onDriverDragCancel();
            return true;

        default:
            return false;
        }
    }
}

/**
 * Class for driving an internal (i.e. not using framework) drag/drop operation.
 */
class InternalDragDriver extends DragDriver {
    InternalDragDriver(final DragController dragController) {
        super(dragController);
    }

    @Override
    public boolean onDragEvent(final DragEvent event) {
        return false;
    }
}
