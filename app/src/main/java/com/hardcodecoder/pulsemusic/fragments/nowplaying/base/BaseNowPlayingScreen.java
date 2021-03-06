package com.hardcodecoder.pulsemusic.fragments.nowplaying.base;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.IBinder;
import android.text.format.DateUtils;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatSeekBar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.GenericTransitionOptions;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.shape.CornerFamily;
import com.google.android.material.slider.Slider;
import com.hardcodecoder.pulsemusic.GlideApp;
import com.hardcodecoder.pulsemusic.PMS;
import com.hardcodecoder.pulsemusic.R;
import com.hardcodecoder.pulsemusic.activities.CurrentPlaylistActivity;
import com.hardcodecoder.pulsemusic.helper.MediaProgressUpdateHelper;
import com.hardcodecoder.pulsemusic.helper.SwipeGestureListener;
import com.hardcodecoder.pulsemusic.model.MusicModel;
import com.hardcodecoder.pulsemusic.singleton.TrackManager;
import com.hardcodecoder.pulsemusic.storage.AppFileManager;
import com.hardcodecoder.pulsemusic.utils.AppSettings;

public abstract class BaseNowPlayingScreen extends Fragment implements MediaProgressUpdateHelper.Callback {

    private final TrackManager mTrackManager = TrackManager.getInstance();
    private ImageView mMediaAlbumCover;
    private MediaController mController;
    private MediaController.TransportControls mTransportControls;
    private MediaProgressUpdateHelper mUpdateHelper;
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            PMS.ServiceBinder serviceBinder = (PMS.ServiceBinder) service;
            mController = serviceBinder.getMediaController();
            mTransportControls = mController.getTransportControls();
            mUpdateHelper = new MediaProgressUpdateHelper(mController, BaseNowPlayingScreen.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };
    // Denotes most recently performed swipe gesture direction, 0 = right, 1 = left
    private int mSwipeDirection = 1;
    private boolean mCurrentItemFavorite = false;

    @CallSuper
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        connectToService();
    }

    @CallSuper
    @Override
    public void onMetadataDataChanged(MediaMetadata metadata) {
        GlideApp.with(this)
                .load(metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART))
                .transition(GenericTransitionOptions.with(mSwipeDirection == 1 ? R.anim.album_cover_enter_left : R.anim.album_cover_enter_right))
                .into(mMediaAlbumCover);
        updateFavoriteItem();
        updateRepeat();
    }

    @CallSuper
    @Override
    public void onPlaybackStateChanged(PlaybackState state) {
        updateRepeat();
    }

    protected void applyCornerRadius(@NonNull ShapeableImageView imageView) {
        float factor = (float) getResources().getDisplayMetrics().densityDpi / DisplayMetrics.DENSITY_DEFAULT;
        int[] radiusDP = AppSettings.getNowPlayingAlbumCoverCornerRadius(imageView.getContext());
        float tl = radiusDP[0] * factor;
        float tr = radiusDP[1] * factor;
        float bl = radiusDP[2] * factor;
        float br = radiusDP[3] * factor;
        int cornerFamily = CornerFamily.ROUNDED;
        imageView.setShapeAppearanceModel(imageView.getShapeAppearanceModel().toBuilder()
                .setTopLeftCorner(cornerFamily, tl)
                .setTopRightCorner(cornerFamily, tr)
                .setBottomLeftCorner(cornerFamily, bl)
                .setBottomRightCorner(cornerFamily, br)
                .build()
        );
    }

    protected void setUpSliderControls(Slider progressSlider) {
        progressSlider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(@NonNull Slider slider) {
                mUpdateHelper.stop();
            }

            @Override
            public void onStopTrackingTouch(@NonNull Slider slider) {
                // Pass progress in milli seconds
                mTransportControls.seekTo((long) slider.getValue() * 1000);
                onProgressValueChanged((int) slider.getValue());
            }
        });
        progressSlider.setLabelFormatter(value -> DateUtils.formatElapsedTime((long) value));
    }

    protected void setUpSeekBarControls(AppCompatSeekBar seekBar) {
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                mUpdateHelper.stop();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Pass progress in milli seconds
                mTransportControls.seekTo((long) seekBar.getProgress() * 1000);
                onProgressValueChanged(seekBar.getProgress());
            }
        });
    }

    protected void resetSliderValues(Slider slider, long valueTo) {
        slider.setValue(0);
        slider.setValueTo(valueTo);
    }

    @SuppressLint("ClickableViewAccessibility")
    protected void setUpAlbumArtImageView(@NonNull ImageView mediaArtImageView) {
        mMediaAlbumCover = mediaArtImageView;
        mMediaAlbumCover.setOnTouchListener(new SwipeGestureListener(mediaArtImageView.getContext()) {
            @Override
            public void onSwipeRight() {
                onSkipToPrevious();
            }

            @Override
            public void onSwipeLeft() {
                onSkipToNext();
            }
        });

    }

    protected void setUpSkipControls(ImageView skipPrev, ImageView skipNext) {
        skipPrev.setOnClickListener(v -> onSkipToPrevious());
        skipNext.setOnClickListener(v -> onSkipToNext());
    }

    protected void toggleRepeatMode() {
        boolean repeat = !mTrackManager.isCurrentTrackInRepeatMode();
        mTrackManager.repeatCurrentTrack(repeat);
        onRepeatStateChanged(repeat);
    }

    protected void toggleFavorite() {
        if (mCurrentItemFavorite) {
            AppFileManager.deleteFavorite(mTrackManager.getActiveQueueItem());
            Toast.makeText(getContext(), getString(R.string.removed_from_fav), Toast.LENGTH_SHORT).show();
        } else {
            if (AppFileManager.addItemToFavorites(mTrackManager.getActiveQueueItem()))
                Toast.makeText(getContext(), getString(R.string.added_to_fav), Toast.LENGTH_SHORT).show();
            else
                Toast.makeText(getContext(), getString(R.string.cannot_add_to_fav), Toast.LENGTH_SHORT).show();
        }
        updateFavoriteItem();
    }

    protected void togglePlayPause() {
        if (null == mController.getPlaybackState()) return;
        PlaybackState state = mController.getPlaybackState();

        if (state.getState() == PlaybackState.STATE_PLAYING) mTransportControls.pause();
        else mTransportControls.play();
    }

    protected void togglePlayPauseAnimation(ImageView playPauseBtn, PlaybackState state) {
        if (null == state || null == getContext() || null == playPauseBtn) return;

        if (state.getState() == PlaybackState.STATE_PLAYING) {
            Drawable d = ContextCompat.getDrawable(getContext(), R.drawable.play_to_pause_linear_out_slow_in);
            playPauseBtn.setImageDrawable(d);
            if (d instanceof AnimatedVectorDrawable) ((AnimatedVectorDrawable) d).start();
        } else {
            Drawable d = ContextCompat.getDrawable(getContext(), R.drawable.pause_to_play);
            playPauseBtn.setImageDrawable(d);
            if (d instanceof AnimatedVectorDrawable) ((AnimatedVectorDrawable) d).start();
        }
    }

    protected String getUpNextText() {
        MusicModel nextItem = TrackManager.getInstance().getNextQueueItem();
        String upNextText;
        if (null != nextItem)
            upNextText = getString(R.string.up_next_title).concat(" ").concat(nextItem.getTrackName());
        else upNextText = getString(R.string.up_next_title_none);
        return upNextText;
    }

    protected String getFormattedElapsedTime(long elapsedTime) {
        return DateUtils.formatElapsedTime(elapsedTime);
    }

    protected void setGotToCurrentQueueCLickListener(View view) {
        view.setOnClickListener(v -> startActivity(new Intent(getContext(), CurrentPlaylistActivity.class)));
    }

    private void updateRepeat() {
        onRepeatStateChanged(mTrackManager.isCurrentTrackInRepeatMode());
    }

    private void updateFavoriteItem() {
        AppFileManager.isItemAFavorite(mTrackManager.getActiveQueueItem(), result ->
                onFavoriteStateChanged((mCurrentItemFavorite = result)));
    }

    private void onSkipToNext() {
        mSwipeDirection = 1;
        mTransportControls.skipToNext();
    }

    private void onSkipToPrevious() {
        mSwipeDirection = 0;
        mTransportControls.skipToPrevious();
    }

    private void connectToService() {
        if (null != getActivity()) {
            Intent intent = new Intent(getActivity(), PMS.class);
            getActivity().bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
        }
    }

    public abstract void onRepeatStateChanged(boolean repeat);

    public abstract void onFavoriteStateChanged(boolean isFavorite);

    @Override
    public void onDestroy() {
        if (null != mUpdateHelper)
            mUpdateHelper.destroy();
        if (null != getActivity())
            getActivity().unbindService(mServiceConnection);
        super.onDestroy();
    }
}