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
package com.donnKey.aesopPlayer.ui.classic;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatButton;

import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.common.base.Preconditions;
import com.donnKey.aesopPlayer.AesopPlayerApplication;
import com.donnKey.aesopPlayer.GlobalSettings;
import com.donnKey.aesopPlayer.R;
import com.donnKey.aesopPlayer.model.AudioBook;
import com.donnKey.aesopPlayer.model.AudioBookManager;
import com.donnKey.aesopPlayer.ui.UiUtil;
import com.donnKey.aesopPlayer.ui.UiControllerBookList;

import java.util.Objects;

import javax.inject.Inject;
import javax.inject.Named;

public class FragmentBookItem extends BookListChildFragment {

    public static FragmentBookItem newInstance(String bookId) {
        FragmentBookItem newFragment = new FragmentBookItem();
        Bundle args = new Bundle();
        args.putString(ARG_BOOK_ID, bookId);
        newFragment.setArguments(args);
        return newFragment;
    }

    @SuppressWarnings("WeakerAccess")
    @Inject public GlobalSettings globalSettings;
    @SuppressWarnings("WeakerAccess")
    @Inject public AudioBookManager audioBookManager;
    @SuppressWarnings("WeakerAccess")
    @Inject @Named("AUDIOBOOKS_DIRECTORY") public String audioBooksDirectoryName;

    private @Nullable UiControllerBookList controller;
    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private UiUtil.SnoozeDisplay snooze;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_book_item, container, false);
        AesopPlayerApplication.getComponent(view.getContext()).inject(this);

        // This should be early so no buttons go live before this
        // TODO: determine if we want to suspend snoozeDelay on initial startup
        snooze = new UiUtil.SnoozeDisplay(this, view, globalSettings);

        Bundle args = getArguments();
        Preconditions.checkNotNull(args);
        final String bookId = args.getString(ARG_BOOK_ID);
        if (bookId != null) {
            AudioBook book = audioBookManager.getById(bookId);
            TextView textView = view.findViewById(R.id.title);
            textView.setText(book.getTitle());
            textView.setTextColor(book.getColourScheme().textColour);
            view.setBackgroundColor(book.getColourScheme().backgroundColour);

            if (book.isDemoSample()) {
                TextView copyBooksInstruction =
                        view.findViewById(R.id.copyBooksInstruction);
                String directoryMessage =
                        getString(R.string.copyBooksInstructionMessage, audioBooksDirectoryName);
                copyBooksInstruction.setText(Html.fromHtml(directoryMessage));
                copyBooksInstruction.setTextColor(book.getColourScheme().textColour);
                copyBooksInstruction.setVisibility(View.VISIBLE);
            }

            // Show the progress in time and percentage
            final TextView progress = view.findViewById(R.id.currentPosition);
            String progressMessage = book.thisBookProgress(getContext());
            progress.setText(progressMessage);

            // Total time
            final TextView displayDuration = view.findViewById(R.id.totalLength);
            long totalMs = book.getTotalDurationMs();
            String duration = UiUtil.formatDuration(totalMs);
            displayDuration.setText(duration);

            // Try to show chapter information
            final TextView chapter = view.findViewById(R.id.chapterInfo);
            String chapterName = book.getChapter();
            chapter.setText(chapterName);

            // Completed
            final TextView completed = view.findViewById(R.id.completed);
            completed.setText(book.getCompleted() ? getText(R.string.bookCompleted) : "");

            final AppCompatButton startButton = view.findViewById(R.id.startButton);
            startButton.setOnClickListener(v -> {
                Preconditions.checkNotNull(controller);
                controller.playCurrentAudiobook();
                startButton.setEnabled(false);
            });
        }

        UiUtil.connectToSettings(view, globalSettings);

        UiUtil.startBlinker(view, globalSettings);

        return view;
    }

    public String getAudioBookId() {
        return Objects.requireNonNull(getArguments()).getString(ARG_BOOK_ID);
    }

    void setController(@NonNull UiControllerBookList controller) {
        this.controller = controller;
    }

    private static final String ARG_BOOK_ID = "bookId";
}