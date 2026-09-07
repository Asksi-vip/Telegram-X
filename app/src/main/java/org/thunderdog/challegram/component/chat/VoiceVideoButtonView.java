/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 10/11/2017
 */
package org.thunderdog.challegram.component.chat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.config.Config;
import org.thunderdog.challegram.navigation.TooltipOverlayView;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.unsorted.Settings;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.ViewUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.lambda.CancellableRunnable;
import me.vkryl.core.lambda.Destroyable;

public class VoiceVideoButtonView extends View implements FactorAnimator.Target, Settings.VideoModePreferenceListener, Destroyable, TooltipOverlayView.LocationProvider {
  private boolean hasTouchControls;

  private final Drawable sendIcon, micIcon, videoIcon, searchIcon;

  public VoiceVideoButtonView (Context context) {
    super(context);
    sendIcon = Drawables.get(getResources(), R.drawable.deproko_baseline_send_24);
    micIcon = Drawables.get(getResources(), R.drawable.deproko_baseline_msg_voice_24);
    videoIcon = Drawables.get(getResources(), R.drawable.deproko_baseline_msg_video_24);
    searchIcon = Drawables.get(getResources(), R.drawable.baseline_search_24);
    setInVideoMode(Settings.instance().preferVideoMode(), false);
  }

  @Override
  public void getTargetBounds (View targetView, Rect outRect) {
    outRect.top += Screen.dp(8f);
    outRect.bottom -= Screen.dp(8f);
  }

  public void setHasTouchControls (boolean hasTouchControls) {
    if (this.hasTouchControls != hasTouchControls) {
      this.hasTouchControls = hasTouchControls;
      if (hasTouchControls) {
        Settings.instance().addVideoPreferenceChangeListener(this);
      } else {
        Settings.instance().removeVideoPreferenceChangeListener(this);
      }
      invalidate();
    }
  }

  private static final int ANIMATOR_VIDEO_MODE = 0, ANIMATOR_SEARCH_MODE = 1;
  private final BoolAnimator inVideoMode = new BoolAnimator(ANIMATOR_VIDEO_MODE, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 120l);
  private final BoolAnimator inSearchMode = new BoolAnimator(ANIMATOR_SEARCH_MODE, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 180l);

  @Override
  public void onPreferVideoModeChanged (boolean preferVideoMode) {
    setInVideoMode(preferVideoMode, hasTouchControls);
  }

  public void setInVideoMode (boolean inVideoMode, boolean animated) {
    this.inVideoMode.setValue(inVideoMode, animated);
  }

  public void setInSearchMode (boolean inSearchMode, boolean animated) {
    this.inSearchMode.setValue(inSearchMode, animated);
  }

  private float sendFactor;

  public void setSendFactor (float factor) {
    if (this.sendFactor != factor) {
      this.sendFactor = factor;
      invalidate();
    }
  }

  @Override
  public void onFactorChanged (int id, float factor, float fraction, FactorAnimator callee) {
    invalidate();
  }

  @Override
  public void onFactorChangeFinished (int id, float finalFactor, FactorAnimator callee) { }

  @Override
  public void performDestroy () {
    Settings.instance().removeVideoPreferenceChangeListener(this);
  }

  private Paint paint;

  private Paint getIconPaint () {
    int color = Theme.getColor(ColorId.circleButtonRegularIcon);
    if (paint == null || paint.getColor() != color)
      paint = Paints.createPorterDuffPaint(paint, color);
    return paint;
  }

  @Override
  protected void onDraw (Canvas c) {
    final int viewWidth = getMeasuredWidth();
    final int viewHeight = getMeasuredHeight();
    final int cx = getPaddingLeft() + (viewWidth - getPaddingLeft() - getPaddingRight()) / 2;
    final int cy = viewHeight / 2;
    final float videoFactor = inVideoMode.getFloatValue();
    final float searchFactor = inSearchMode.getFloatValue();

    final Paint paint = hasTouchControls ? Paints.getIconGrayPorterDuffPaint() : getIconPaint();
    final int savedAlpha = paint.getAlpha();

    // Apple iMessage: blue circle always visible, fading based on sendFactor
    final float circleRadius = Screen.dp(18f);
    final Paint blueCirclePaint = Paints.fillingPaint(android.graphics.Color.parseColor("#007AFF"));
    final float circleAlpha = Math.max(sendFactor, 1f - searchFactor);
    blueCirclePaint.setAlpha((int) (255f * circleAlpha));
    c.drawCircle(cx, cy, circleRadius, blueCirclePaint);

    // White icons on blue circle
    final Paint whitePaint = Paints.fillingPaint(android.graphics.Color.WHITE);
    if (sendFactor > 0f) {
      // Send mode: arrow-up icon
      whitePaint.setAlpha((int) (255f * sendFactor));
      Drawables.drawCentered(c, sendIcon, cx, cy, whitePaint);
    } else {
      // Idle mode: mic/video icon
      final float iconAlpha = 1f - searchFactor;
      if (iconAlpha > 0f) {
        int y = (int) (cy + (cy + micIcon.getMinimumHeight() / 2) * videoFactor);
        whitePaint.setAlpha((int) (255f * (1f - videoFactor) * iconAlpha));
        Drawables.drawCentered(c, micIcon, cx, y, whitePaint);
        y = (int) (cy - (cy + videoIcon.getMinimumHeight() / 2) * (1f - videoFactor));
        whitePaint.setAlpha((int) (255f * videoFactor * iconAlpha));
        Drawables.drawCentered(c, videoIcon, cx, y, whitePaint);
      }
    }

    paint.setAlpha(savedAlpha);
  }

  private float trackDownX, trackDownY;

  private boolean inLongTap;

  private boolean draggingUp, draggingLeft;

  private boolean isDown;

  private void setIsDown (boolean isDown) {
    if (this.isDown != isDown) {
      this.isDown = isDown;
      if (isDown) {
        scheduleLongTap();
      } else {
        cancelLongTap();
      }
    }
  }

  private CancellableRunnable scheduledLongTap;

  private void scheduleLongTap () {
    cancelLongTap();
    scheduledLongTap = new CancellableRunnable() {
      @Override
      public void act () {
        if (scheduledLongTap == this) {
          setInLongTap(true, false);
          scheduledLongTap = null;
        }
      }
    };
    postDelayed(scheduledLongTap, 120l);
  }

  private void cancelLongTap () {
    if (scheduledLongTap != null) {
      scheduledLongTap.cancel();
      scheduledLongTap = null;
    }
  }

  private void setInLongTap (boolean inLongTap, boolean byCancel) {
    if (this.inLongTap != inLongTap) {
      boolean success;
      if (inLongTap) {
        success = UI.getContext(getContext()).getRecordAudioVideoController().startRecording(this, false);
      } else {
        if (!cancelLongTap) {
          UI.getContext(getContext()).getRecordAudioVideoController().finishRecording(byCancel);
        }
        success = true;
      }
      if (success) {
        this.inLongTap = inLongTap;
      } else if (inLongTap) {
        setIsDown(false);
      }
    }
  }

  private void performTap () {
    ViewUtils.onClick(this);
    Settings.instance().setPreferVideoMode(!inVideoMode.getValue());
  }

  private void checkDraggingCircle () {
    boolean isDragging = draggingUp && isDown;
    UI.getContext(getContext()).getRecordAudioVideoController().setApplyVerticalDrag(isDragging);
  }

  private boolean cancelLongTap;

  @Override
  public boolean onTouchEvent (MotionEvent e) {
    if (!hasTouchControls) {
      return false;// e.getAction() != MotionEvent.ACTION_DOWN || (getAlpha() > 0f && getVisibility() == View.VISIBLE);
    }

    switch (e.getAction()) {
      case MotionEvent.ACTION_DOWN: {
        trackDownX = e.getX();
        trackDownY = e.getY();
        draggingLeft = draggingUp = false;
        cancelLongTap = false;

        setIsDown(true);
        break;
      }
      case MotionEvent.ACTION_MOVE: {
        if (isDown) {
          float x = e.getX();
          float y = e.getY();
          if (inLongTap && !(cancelLongTap || (cancelLongTap = !UI.getContext(getContext()).getRecordAudioVideoController().isOpen()))) {
            float diffX = Math.max(0f, (trackDownX - x));
            float diffY = Math.max(0f, (trackDownY - y));
            boolean draggingUp = diffY >= Screen.getTouchSlop() && diffY >= diffX - Screen.getTouchSlop();
            boolean draggingLeft = diffX >= Screen.getTouchSlop() && !draggingUp;
            if (!draggingLeft && !draggingUp) {
              draggingLeft = this.draggingLeft;
              draggingUp = this.draggingUp;
            }
            if (draggingLeft || draggingUp) {
              if (!this.draggingLeft && !this.draggingUp) {
                trackDownX = x;
                trackDownY = y;
                diffX = diffY = 0;
              }
              this.draggingLeft = draggingLeft;
              this.draggingUp = draggingUp;
              checkDraggingCircle();
              if (!UI.getContext(getContext()).getRecordAudioVideoController().setTranslations(-diffX, -diffY)) {
                cancelLongTap = true;
              }
            }
          } else {
            trackDownX = x;
            trackDownY = y;
          }
        }
        break;
      }
      case MotionEvent.ACTION_UP: {
        if (isDown) {
          if (inLongTap) {
            setInLongTap(false, false);
          } else {
            performTap();
          }
          setIsDown(false);
        }
        break;
      }
      case MotionEvent.ACTION_CANCEL: {
        if (isDown) {
          setInLongTap(false, true);
          setIsDown(false);
        }
        break;
      }
    }

    return true;
  }
}
