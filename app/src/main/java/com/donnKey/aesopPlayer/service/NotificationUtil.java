/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2018-2019 Donn S. Terry
 * Copyright (c) 2015-2017 Marcin Simonides
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
package com.donnKey.aesopPlayer.service;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import androidx.core.app.NotificationCompat;

import com.google.common.base.Preconditions;
import com.donnKey.aesopPlayer.R;
import com.donnKey.aesopPlayer.ui.MainActivity;

public class NotificationUtil {

    // This channel is also used for samples download notifications but I guess
    // it's better to reuse the channel instead of providing another one just for
    // one-time action.
    private static final String PLAYBACK_SERVICE_CHANNEL_ID = "playback";

    static Notification createForegroundServiceNotification(
            Context context, int stringId, int drawableId) {
        Intent activityIntent = new Intent(context, MainActivity.class);
        activityIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent intent = PendingIntent.getActivity(
                context, 0, activityIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(context, PLAYBACK_SERVICE_CHANNEL_ID)
                .setContentTitle(context.getResources().getString(stringId))
                .setContentIntent(intent)
                .setSmallIcon(drawableId)
                .setOngoing(true)
                .build();
    }

    @TargetApi(26)
    public static class API26 {
        public  static void registerPlaybackServiceChannel(Context context) {
            NotificationManager manager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            Preconditions.checkNotNull(manager);

            NotificationChannel channel = new NotificationChannel(
                    PLAYBACK_SERVICE_CHANNEL_ID,
                    context.getString(R.string.notificationChannelPlayback),
                    NotificationManager.IMPORTANCE_LOW);
            manager.createNotificationChannel(channel);
        }
    }
}
