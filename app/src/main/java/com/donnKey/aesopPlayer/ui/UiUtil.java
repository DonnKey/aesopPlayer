/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018-2020 Donn S. Terry
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.donnKey.aesopPlayer.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.CountDownTimer;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.fragment.app.Fragment;

import com.google.common.base.Preconditions;
import com.donnKey.aesopPlayer.GlobalSettings;
import com.donnKey.aesopPlayer.R;
import com.donnKey.aesopPlayer.ui.provisioning.ProvisioningActivity;

import java.util.concurrent.TimeUnit;

import javax.inject.Singleton;

@Singleton
public class UiUtil {
    @SuppressWarnings("unused")
    private static final String TAG = "UiUtil";

    public static class SnoozeDisplay {
        private View snoozeOverlay;
        private TextView snoozeCounter;
        static private int enabled = 0;

        @SuppressWarnings("SameReturnValue")
        @SuppressLint("ClickableViewAccessibility")
        private void SnoozeDisplayFor(
                final Fragment fragment,
                final View view,
                final int time) {

            if (enabled <= 0) {
                return;
            }
            enabled--;

            if (time <= 0) {
                return;
            }

            snoozeOverlay = view.findViewById(R.id.snoozeOverlay);
            Preconditions.checkNotNull(snoozeOverlay);

            snoozeCounter = view.findViewById(R.id.snoozeCounter);
            Preconditions.checkNotNull(snoozeCounter);

            snoozeCounter.setOnTouchListener((v, event) -> {
                // Don't let any events "through" the overlay.
                return true;
            });
            snoozeOverlay.setVisibility(View.VISIBLE);

            new CountDownTimer((time + 1) * 1000, 1000) // Wait time secs, tick every 1 sec
            {
                @Override
                public final void onTick(final long millisUntilFinished) {
                    try {
                        long secsRemaining = millisUntilFinished / 1000;
                        if (secsRemaining <= 1) {
                            snoozeCounter.setText("");
                        } else {
                            snoozeCounter.setText(fragment.getString(R.string.snooze_seconds, secsRemaining));
                        }
                    }
                    catch (Exception e) {
                        /* Occasionally the fragment isn't attached to any Activity during
                           Activity stop/start, and we don't care when the screen is that busy anyway.
                         */
                    }
                }

                @Override
                public final void onFinish() {
                    snoozeOverlay.setVisibility(View.GONE);
                    snoozeOverlay = null;
                    snoozeCounter = null;
                }
            }.start();
        }

        public SnoozeDisplay(Fragment fragment, View view, @NonNull GlobalSettings globalSettings) {
            int time = globalSettings.getSnoozeDelay();
            SnoozeDisplayFor(fragment, view, time);
        }

        public static void enableOneUse() {
            enabled++;
        }
        public static void disableOneUse() {
            enabled--;
        }
    }

    public static void startBlinker(View view, @NonNull GlobalSettings globalSettings) {
        ViewFlipper flipper;

        // time in MS for a cycle
        int time = globalSettings.getBlinkRate();
        if (time <= 0) {
            return;
        }

        flipper = view.findViewById(R.id.flipper);
        if (time > 300) {
            flipper.setInAnimation(AnimationUtils.loadAnimation(view.getContext(), android.R.anim.fade_in));
            flipper.setOutAnimation(AnimationUtils.loadAnimation(view.getContext(), android.R.anim.fade_out));
        }
        flipper.setFlipInterval(time);
        flipper.startFlipping();
    }

    // Three ways to get to settings, with thought to addressing accidental
    // mishandling of the phone:
    // 1) Just like most apps, tap the gear icon.
    // 2) Hold one gear icon, then simultaneously press a new one that appears.
    // 3) Multiple taps on the gear icon.
    // If none of those prove resistant to mishandling, settings button followed
    // by a volume key looks like a possibility.
    @SuppressLint("ClickableViewAccessibility") // Accessibility not appropriate for some options
    public static void connectToSettings(@NonNull View view, @NonNull GlobalSettings globalSettings) {

        // All versions start with this gone, so be sure
        final View settingsButton2box = view.findViewById(R.id.settingsButton2box);
        settingsButton2box.setVisibility(View.GONE);

        final Context context = view.getContext();
        final AppCompatActivity activity = (AppCompatActivity)context;
        final AppCompatButton settingsButton = view.findViewById(R.id.settingsButton);

        switch (globalSettings.getSettingsInterlock()) {
            case NONE: {
                settingsButton.setOnClickListener(v -> {
                    activity.startActivity(new Intent(context, ProvisioningActivity.class));
                    settingsButton.setEnabled(false);
                });
                break;
            }
            case DOUBLE_PRESS: {
                settingsButton.setOnTouchListener(new PressListener(view));
                break;
            }
            case MULTI_TAP: {
                settingsButton.setOnTouchListener(new MultitapTouchListener(
                        context, () -> activity.startActivity(new Intent(context, ProvisioningActivity.class))));
                break;
            }
        }
    }

    static private class PressListener implements View.OnTouchListener {
        private final View pressListener;
        private final @NonNull Context context;
        final AppCompatActivity activity;
        private Toast lastToast;

        PressListener(@NonNull View view){
            context = view.getContext();
            activity = (AppCompatActivity)context;
            pressListener = view;
        }

        @SuppressLint("ClickableViewAccessibility") // Accessibility not appropriate for this option
        @Override
        public boolean onTouch(View view, @NonNull MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN: {
                    lastToast = Toast.makeText(context, R.string.press_other_gear_prompt, Toast.LENGTH_LONG);
                    lastToast.show();

                    final View settingsButton2box = pressListener.findViewById(R.id.settingsButton2box);
                    settingsButton2box.setVisibility(View.VISIBLE);

                    final AppCompatButton settingsButton2 = pressListener.findViewById(R.id.settingsButton2);
                    settingsButton2.setOnTouchListener((v, event1) -> {
                        lastToast.cancel();
                        if (event1.getAction() == MotionEvent.ACTION_UP) {
                            activity.startActivity(new Intent(context, ProvisioningActivity.class));
                        }
                        return true;
                    });
                    break;
                }
                case MotionEvent.ACTION_UP: {
                    final View settingsButton2box = pressListener.findViewById(R.id.settingsButton2box);
                    settingsButton2box.setVisibility(View.GONE);
                    break;
                }
                default:
                    return false;
            }
            return true;
        }
    }

    // Where we are in the current book
    @NonNull
    @SuppressLint("DefaultLocale")
    public static String formatDuration(long currentMs) {
        long hours = TimeUnit.MILLISECONDS.toHours(currentMs);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(currentMs) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(currentMs) % 60;

        return String.format("%d:%02d:%02d", hours, minutes, seconds);
    }

    @NonNull
    @SuppressLint("DefaultLocale")
    public static String formatDurationShort(long currentMs) {
        long hours = TimeUnit.MILLISECONDS.toHours(currentMs);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(currentMs) % 60;

        return String.format("%d:%02d", hours, minutes);
    }

    @ColorInt
    public static int colorFromAttribute(@NonNull Context context, @AttrRes int attributeId) {
        TypedValue value = new TypedValue();
        context.getTheme().resolveAttribute(attributeId, value, true);
        return context.getResources().getColor(value.resourceId);
    }
}
