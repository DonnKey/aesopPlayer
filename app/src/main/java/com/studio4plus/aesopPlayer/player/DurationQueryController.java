package com.studio4plus.aesopPlayer.player;

import java.io.File;

public interface DurationQueryController {

    interface Observer {
        void onDuration(File file, long durationMs);
        void onFinished();
        void onPlayerReleased();
        void onPlayerError(File path);
    }

    void start(Observer observer);
    void stop();
}
