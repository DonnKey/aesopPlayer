package com.studio4plus.homerplayer.ui;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.util.Log;

import com.crashlytics.android.Crashlytics;
import com.google.common.base.Preconditions;
import com.studio4plus.homerplayer.GlobalSettings;
import com.studio4plus.homerplayer.GlobalSettings.FaceDownAction;
import com.studio4plus.homerplayer.R;
import com.studio4plus.homerplayer.analytics.AnalyticsTracker;
import com.studio4plus.homerplayer.events.AudioBooksChangedEvent;
import com.studio4plus.homerplayer.events.PlaybackFatalErrorEvent;
import com.studio4plus.homerplayer.events.PlaybackStoppedEvent;
import com.studio4plus.homerplayer.model.AudioBook;
import com.studio4plus.homerplayer.model.AudioBookManager;
import com.studio4plus.homerplayer.service.PlaybackService;
import com.studio4plus.homerplayer.service.DeviceMotionDetector;

import javax.inject.Inject;

import de.greenrobot.event.EventBus;

import static com.studio4plus.homerplayer.GlobalSettings.FaceDownAction.NONE;
import static com.studio4plus.homerplayer.GlobalSettings.FaceDownAction.STOP_RESUME;

public class UiControllerMain implements ServiceConnection {

    private final @NonNull Activity activity;
    private final @NonNull MainUi mainUi;
    private final @NonNull AudioBookManager audioBookManager;
    private final @NonNull EventBus eventBus;
    private final @NonNull AnalyticsTracker analyticsTracker;
    private final @NonNull UiControllerNoBooks.Factory noBooksControllerFactory;
    private final @NonNull UiControllerBookList.Factory bookListControllerFactory;
    private final @NonNull UiControllerPlayback.Factory playbackControllerFactory;
    private final @NonNull GlobalSettings globalSettings;

    private static final int PERMISSION_REQUEST_FOR_BOOK_SCAN = 1;
    private static final String TAG = "UiControllerMain";

    private @Nullable PlaybackService playbackService;

    private @NonNull State currentState = new InitState();

    @Inject
    UiControllerMain(@NonNull Activity activity,
                     @NonNull MainUi mainUi,
                     @NonNull AudioBookManager audioBookManager,
                     @NonNull EventBus eventBus,
                     @NonNull AnalyticsTracker analyticsTracker,
                     @NonNull UiControllerNoBooks.Factory noBooksControllerFactory,
                     @NonNull UiControllerBookList.Factory bookListControllerFactory,
                     @NonNull UiControllerPlayback.Factory playbackControllerFactory,
                     @NonNull GlobalSettings globalSettings) {
        this.activity = activity;
        this.mainUi = mainUi;
        this.audioBookManager = audioBookManager;
        this.eventBus = eventBus;
        this.analyticsTracker = analyticsTracker;
        this.noBooksControllerFactory = noBooksControllerFactory;
        this.bookListControllerFactory = bookListControllerFactory;
        this.playbackControllerFactory = playbackControllerFactory;
        this.globalSettings = globalSettings;
    }

    void onActivityCreated() {
        eventBus.register(this);
        Intent serviceIntent = new Intent(activity, PlaybackService.class);
        activity.bindService(serviceIntent, this, Context.BIND_AUTO_CREATE);
    }

    void onActivityStart() {
        Crashlytics.log(Log.DEBUG, TAG,"activity start");
        scanAudioBookFiles();
        maybeSetInitialState();
    }

    void onActivityPause() {
        currentState.onActivityPause();
    }

    void onActivityStop() {
        Crashlytics.log(Log.DEBUG, TAG,
                "UI: leave state " + currentState.debugName() + " (activity stop)");
        currentState.onLeaveState();
        currentState = new InitState();
    }

    void onActivityDestroy() {
        activity.unbindService(this);
        eventBus.unregister(this);
    }

    @SuppressWarnings({"UnusedParameters", "UnusedDeclaration"})
    public void onEvent(AudioBooksChangedEvent event) {
        currentState.onBooksChanged(this);
        maybeSetInitialState();
    }

    @SuppressWarnings({"UnusedParameters", "UnusedDeclaration"})
    public void onEvent(PlaybackStoppedEvent event) {
        currentState.onPlaybackStop(this);
    }

    @SuppressWarnings({"UnusedDeclaration"})
    public void onEvent(PlaybackFatalErrorEvent event) {
        mainUi.onPlaybackError(event.path);
    }

    void playCurrentAudiobook() {
        Preconditions.checkNotNull(currentAudioBook());
        changeState(StateFactory.PLAYBACK);
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder service) {
        Crashlytics.log(Log.DEBUG, TAG, "onServiceConnected");
        Preconditions.checkState(playbackService == null);
        playbackService = ((PlaybackService.ServiceBinder) service).getService();
        maybeSetInitialState();
    }

    @NonNull
    private Activity getActivity()
    {
        return activity;
    }

    @NonNull
    private GlobalSettings getGlobalSettings()
    {
        return globalSettings;
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        Crashlytics.log(Log.DEBUG, TAG, "onServiceDisconnected");
        playbackService = null;
    }

    void onRequestPermissionResult(
            int code, final @NonNull String[] permissions, final @NonNull int[] grantResults) {
        switch (code) {
            case PERMISSION_REQUEST_FOR_BOOK_SCAN:
                if (grantResults.length == 2 && grantResults[0] == PackageManager.PERMISSION_GRANTED
                        && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                    scanAudioBookFiles();
                } else {
                    boolean canRetry =
                            ActivityCompat.shouldShowRequestPermissionRationale(
                                    activity, Manifest.permission.READ_EXTERNAL_STORAGE) ||
                                    ActivityCompat.shouldShowRequestPermissionRationale(
                                            activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
                    AlertDialog.Builder dialogBuilder = PermissionUtils.permissionRationaleDialogBuilder(
                            activity, R.string.permission_rationale_scan_audiobooks);
                    if (canRetry) {
                        dialogBuilder.setPositiveButton(
                                R.string.permission_rationale_try_again,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        PermissionUtils.checkAndRequestPermission(
                                                activity, permissions, PERMISSION_REQUEST_FOR_BOOK_SCAN);
                                    }
                                });
                    } else {
                        analyticsTracker.onPermissionRationaleShown("audiobooksScan");
                        dialogBuilder.setPositiveButton(
                                R.string.permission_rationale_settings,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        PermissionUtils.openAppSettings(activity);
                                    }
                                });
                    }
                    dialogBuilder.setNegativeButton(
                            R.string.permission_rationale_exit, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    activity.finish();
                                }
                            })
                            .create().show();
                }
                break;
            case UiControllerNoBooks.PERMISSION_REQUEST_DOWNLOADS:
                currentState.onRequestPermissionResult(code, grantResults);
                break;
        }
    }

    private void scanAudioBookFiles() {
        if (PermissionUtils.checkAndRequestPermission(
                activity,
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE},
                PERMISSION_REQUEST_FOR_BOOK_SCAN))
            audioBookManager.scanFiles();
    }

    private void maybeSetInitialState() {
        if (currentState instanceof InitState && playbackService != null &&
                audioBookManager.isInitialized()) {

            if (playbackService.getState() != PlaybackService.State.IDLE) {
                Preconditions.checkState(hasAnyBooks());
                changeState(StateFactory.PLAYBACK);
            } else if (hasAnyBooks()) {
                changeState(StateFactory.BOOK_LIST);
            } else {
                changeState(StateFactory.NO_BOOKS);
            }
        }
    }

    private boolean hasAnyBooks() {
        return !audioBookManager.getAudioBooks().isEmpty();
    }

    private AudioBook currentAudioBook() {
        return audioBookManager.getCurrentBook();
    }

    private void changeState(StateFactory newStateFactory) {
        Crashlytics.log(Log.DEBUG, TAG, "UI: leave state: " + currentState.debugName());
        currentState.onLeaveState();
        Crashlytics.log(Log.DEBUG, TAG,"UI: create state: " + newStateFactory.name());
        currentState = newStateFactory.create(this, currentState);
    }

    @NonNull
    private UiControllerBookList showBookList(boolean animate) {
        analyticsTracker.onBookListDisplayed();
        BookListUi bookListUi = mainUi.switchToBookList(animate);
        return bookListControllerFactory.create(this, bookListUi);
    }

    @NonNull
    private UiControllerNoBooks showNoBooks(boolean animate) {
        NoBooksUi noBooksUi = mainUi.switchToNoBooks(animate);
        return noBooksControllerFactory.create(noBooksUi);
    }

    @NonNull
    private UiControllerPlayback showPlayback(boolean animate) {
        Preconditions.checkNotNull(playbackService);
        PlaybackUi playbackUi = mainUi.switchToPlayback(animate);
        return playbackControllerFactory.create(playbackService, playbackUi);
    }

    private enum StateFactory {
        NO_BOOKS {
            @Override
            State create(@NonNull UiControllerMain mainController, @NonNull State previousState) {
                return new NoBooksState(mainController, previousState);
            }
        },
        BOOK_LIST {
            @Override
            State create(@NonNull UiControllerMain mainController, @NonNull State previousState) {
                return new BookListState(mainController, previousState);
            }
        },
        PLAYBACK {
            @Override
            State create(@NonNull UiControllerMain mainController, @NonNull State previousState) {
                return new PlaybackState(mainController, previousState);
            }
        };

        abstract State create(
                @NonNull UiControllerMain mainController, @NonNull State previousState);
    }

    private static abstract class State {
        abstract void onLeaveState();
        static boolean stoppedByFaceDown = false;
        static FaceDownAction faceDownAction = NONE;

        void onPlaybackStop(@NonNull UiControllerMain mainController) {
            //noinspection ConstantConditions - getting here is an error
            Preconditions.checkState(false);
        }

        void onBooksChanged(@NonNull UiControllerMain mainController) {
            //noinspection ConstantConditions - getting here is an error
            Preconditions.checkState(false);
        }

        void onActivityPause() { }

        void onRequestPermissionResult(int code, @NonNull int[] grantResults) {
            //noinspection ConstantConditions - getting here is an error
            Preconditions.checkState(false);
        }

        abstract @NonNull String debugName();
    }

    private static class InitState extends State {
        @Override
        void onPlaybackStop(@NonNull UiControllerMain mainController) { }

        @Override
        void onBooksChanged(@NonNull UiControllerMain mainController) { }

        @Override
        void onLeaveState() { }

        @Override
        @NonNull String debugName() { return "InitState"; }
    }

    private static class NoBooksState extends State {
        private @NonNull final UiControllerNoBooks controller;

        NoBooksState(@NonNull UiControllerMain mainController, @NonNull State previousState) {
            this.controller = mainController.showNoBooks(!(previousState instanceof InitState));
            stoppedByFaceDown = false;
        }

        @Override
        public void onLeaveState() {
            controller.shutdown();
        }

        @Override
        public void onBooksChanged(@NonNull UiControllerMain mainController) {
            if (mainController.hasAnyBooks())
                mainController.changeState(StateFactory.BOOK_LIST);
        }

        @Override
        void onRequestPermissionResult(int code, @NonNull int[] grantResults) {
            controller.onRequestPermissionResult(code, grantResults);
        }

        @Override
        @NonNull String debugName() { return "NoBooksState"; }
    }

    private static class BookListState extends State
            implements DeviceMotionDetector.Listener {
        private @NonNull final UiControllerBookList controller;
        private @NonNull final DeviceMotionDetector motionDetector;
        private @NonNull final State prevState;

        BookListState(@NonNull UiControllerMain mainController, @NonNull State previousState) {
            prevState = previousState;
            controller = mainController.showBookList(!(previousState instanceof InitState));
            //noinspection RedundantCast
            motionDetector = DeviceMotionDetector.getDeviceMotionDetector((Context)mainController.getActivity(), this);
            // Avoid starting playback via incidental motion of the phone
            if (stoppedByFaceDown && faceDownAction == STOP_RESUME ) {
                motionDetector.enable();
            }
            faceDownAction = mainController.getGlobalSettings().getStopOnFaceDown();
            stoppedByFaceDown = false;
        }

        @Override
        void onLeaveState() {
            motionDetector.disable();
            controller.shutdown();
        }

        @Override
        void onBooksChanged(@NonNull UiControllerMain mainController) {
            if (!mainController.hasAnyBooks()) {
                motionDetector.disable();
                mainController.changeState(StateFactory.NO_BOOKS);
            }
        }

        @Override
        public void onFaceDownStill() { /* ignore */ }

        @Override
        public void onFaceUpStill() {
            motionDetector.disable();
            if (prevState instanceof PlaybackState) {
                controller.playCurrentAudiobook();
            }
        }

        @Override
        public void onSignificantMotion() { /* ignore */ }

        @Override
        @NonNull String debugName() { return "BookListState"; }

    }

    private static class PlaybackState extends State
            implements DeviceMotionDetector.Listener {
        private @NonNull final UiControllerPlayback controller;
        private @Nullable AudioBook playingAudioBook;
        private @NonNull final DeviceMotionDetector motionDetector;

        PlaybackState(@NonNull UiControllerMain mainController, @NonNull State previousState) {
            controller = mainController.showPlayback(!(previousState instanceof InitState));
            playingAudioBook = mainController.currentAudioBook();
            if (!(previousState instanceof InitState)) {
                Preconditions.checkNotNull(playingAudioBook);
                controller.startPlayback(playingAudioBook);
            }
            //noinspection RedundantCast
            motionDetector = DeviceMotionDetector.getDeviceMotionDetector((Context)mainController.getActivity(), this);
            if (faceDownAction != NONE )
                motionDetector.enable();
            faceDownAction = mainController.getGlobalSettings().getStopOnFaceDown();
            stoppedByFaceDown = false;
        }

        @Override
        void onActivityPause() {
            Preconditions.checkNotNull(controller);
            controller.stopRewindIfActive();
        }

        @Override
        void onLeaveState() {
            motionDetector.disable();
            Preconditions.checkNotNull(controller);
            controller.shutdown();
        }

        @Override
        void onPlaybackStop(@NonNull UiControllerMain mainController) {
            motionDetector.disable();
            mainController.changeState(mainController.hasAnyBooks()
                    ? StateFactory.BOOK_LIST
                    : StateFactory.NO_BOOKS);
        }

        @Override
        void onBooksChanged(@NonNull UiControllerMain mainController) {
            motionDetector.disable();
            if (playingAudioBook != null &&
                    playingAudioBook != mainController.currentAudioBook()) {
                controller.stopPlayback();
                playingAudioBook = null;
            }
        }

        @Override
        public void onFaceDownStill() {
            // Stop playback
            stoppedByFaceDown = true;
            controller.stopPlayback();
        }

        @Override
        public void onFaceUpStill() {
            /* ignore */
        }

        @Override
        public void onSignificantMotion() {
            controller.playbackService.resetSleepTimer();
        }

        @Override
        @NonNull String debugName() { return "PlaybackState"; }
    }

}
