package com.donnKey.aesopPlayer.ui;

import android.content.Context;
import android.util.Log;

import com.crashlytics.android.Crashlytics;
import com.donnKey.aesopPlayer.model.AudioBook;
import com.donnKey.aesopPlayer.player.PlaybackController;
import com.donnKey.aesopPlayer.player.Player;

import java.io.File;

import de.greenrobot.event.EventBus;

/**
 * Plays the current audiobook for a short amount of time. Just to demonstrate.
 */
public class SnippetPlayer implements PlaybackController.Observer {

    private static final long PLAYBACK_TIME_MS = 5000;

    private static final String TAG = "SnippetPlayer";
    private final PlaybackController playbackController;
    private long startPositionMs = -1;
    private boolean isPlaying = false;

    public SnippetPlayer(Context context, EventBus eventBus, float playbackSpeed) {
        Player player = new Player(context, eventBus);
        player.setPlaybackSpeed(playbackSpeed);
        playbackController = player.createPlayback();
        playbackController.setObserver(this);
    }

    public void play(AudioBook audioBook) {
        AudioBook.Position position = audioBook.getLastPosition();

        isPlaying = true;
        playbackController.start(position.getFile(), position.seekPosition);
    }

    public void stop() {
        playbackController.stop();
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    @Override
    public void onDuration(File file, long durationMs) {}

    @Override
    public void onPlaybackProgressed(long currentPositionMs) {
        if (startPositionMs < 0) {
            startPositionMs = currentPositionMs;
        } else {
            if (currentPositionMs - startPositionMs > PLAYBACK_TIME_MS) {
                playbackController.stop();
            }
        }
    }

    @Override
    public void onPlaybackEnded() {}

    @Override
    public void onPlaybackStopped(long currentPositionMs) {}

    @Override
    public void onPlaybackError(File path) {
        Crashlytics.log(Log.DEBUG, TAG,"Unable to play snippet: " + path.toString());
    }

    @Override
    public void onPlayerReleased() {
        isPlaying = false;
    }
}