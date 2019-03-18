package com.studio4plus.homerplayer.ui;

import android.content.Context;
import android.speech.tts.TextToSpeech;

import com.google.common.base.Preconditions;
import com.studio4plus.homerplayer.concurrency.SimpleFuture;

import java.util.HashMap;
import java.util.Locale;
import java.util.Objects;

import javax.inject.Singleton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

@Singleton
public class Speaker implements TextToSpeech.OnInitListener {

    // TTS is usually much louder than a regular audio book recording.
    private static final String TTS_VOLUME_ADJUSTMENT = "0.5";

    private final Locale locale;
    private final TextToSpeech tts;
    private final HashMap<String, String> speechParams = new HashMap<>();

    private boolean ttsReady;  // (Finally!) everything's set up
    private boolean ttsFailed; // hard failure - remember to ignore calls

    private @Nullable SimpleFuture<Object> speakerFuture;
    private @Nullable SimpleFuture.Listener<Object> speakerListener;

    private static Speaker thisSpeaker;

    private String pendingUtterance = "";

    public static Speaker get(Context context, SpeakerProvider speakerProvider) {
        if (thisSpeaker != null) {
            return thisSpeaker;
        }

        thisSpeaker = new Speaker(context, speakerProvider);
        return thisSpeaker;
    }

    @NonNull
    public static Speaker get() {
        Preconditions.checkState(thisSpeaker != null,
                "Must call 2 argument Speaker.get first");
        return thisSpeaker;
    }

    public float getVolume() {
        String s = speechParams.get(TextToSpeech.Engine.KEY_PARAM_VOLUME);
        return  Float.parseFloat(Objects.requireNonNull(s));
    }

    public void setVolume(float v) {
        speechParams.put(TextToSpeech.Engine.KEY_PARAM_VOLUME, Float.toString(v));
    }

    private Speaker(@NonNull Context context, @NonNull SpeakerProvider speakerProvider) {
        ttsReady = ttsFailed = false;

        this.locale = context.getResources().getConfiguration().locale;

        this.tts = new TextToSpeech(context, this);
        speechParams.put(TextToSpeech.Engine.KEY_PARAM_VOLUME, TTS_VOLUME_ADJUSTMENT);

        // Fire off the Activity to get speech and await the result (which could be a long time).
        // OnSpeakerObtained is called when done (success or not). We need the Future to
        // know if it's a hard fail. Otherwise we could just wait for onInit.
        speakerFuture = speakerProvider.obtainTts();
        speakerListener = new SimpleFuture.Listener<Object>() {
            @Override
            public void onResult(@NonNull Object result) {
                onSpeakerObtained(true);
            }
            @Override
            public void onException(@NonNull Throwable t) {
                onSpeakerObtained(false);
            }
        };
        speakerFuture.addListener(speakerListener);
    }

    private void onSpeakerObtained(boolean OK) {
        speakerFuture = null;
        speakerListener = null;
        ttsFailed = !OK;
        if (ttsFailed) {
            pendingUtterance = "";
        }
        // Nothing else, we have to wait for onInit to be ready to speak
    }

    // From tts engine whenever it's really ready; it's indeterminate whether onSpeakerObtained or
    // onInit will be called first. Experimentally, it varies.
    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true;
            tts.setLanguage(locale);
            tts.speak(pendingUtterance, TextToSpeech.QUEUE_FLUSH, speechParams);
        }
        pendingUtterance = "";
    }

    // Actually speak, accumulating text until we have a speech engine
    public void speak(String text) {
        if (ttsFailed) {
            return;  // do nothing, be sure not to accumulate strings
        }
        if (pendingUtterance.length()>0) {
            pendingUtterance += " - " + text;
            // The hyphen makes it sound a little better (not run together)
        }
        else {
            pendingUtterance = text;
        }
        if (ttsReady) {
            tts.speak(pendingUtterance, TextToSpeech.QUEUE_FLUSH, speechParams);
            pendingUtterance = "";
        }
    }

    // Called from Application.onTerminate() because anything sooner (such as
    // MainActivity.onDestroy) cancels some speech when running with short display-
    // active periods or starting with a black screen.
    // I haven't actually seen a call to onTerminate, but our call is there.
    private void innerShutdown() {
        pendingUtterance = "";

        if (ttsReady) {
            tts.shutdown();
        }
        ttsReady = false;

        if (speakerFuture != null) {
            Preconditions.checkNotNull(speakerListener);
            speakerFuture.removeListener(speakerListener);
            speakerFuture = null;
        }
    }

    public static void shutdown() {
        Speaker.get(null,null).innerShutdown();
    }
}
