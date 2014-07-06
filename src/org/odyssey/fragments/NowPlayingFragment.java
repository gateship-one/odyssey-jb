package org.odyssey.fragments;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import org.odyssey.MainActivity;
import org.odyssey.NowPlayingInformation;
import org.odyssey.R;
import org.odyssey.manager.AsyncLoader;
import org.odyssey.playbackservice.PlaybackService;
import org.odyssey.playbackservice.PlaybackService.RANDOMSTATE;
import org.odyssey.playbackservice.PlaybackService.REPEATSTATE;
import org.odyssey.playbackservice.PlaybackServiceConnection;
import org.odyssey.playbackservice.TrackItem;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

public class NowPlayingFragment extends Fragment implements OnSeekBarChangeListener {

    private TextView mTitleTextView;
    private TextView mAlbumTextView;
    private TextView mArtistTextView;
    private TextView mMinDuration;
    private TextView mMaxDuration;
    private ImageView mCoverImageView;
    private SeekBar mSeekBar;
    private PlaybackServiceConnection mServiceConnection;
    private Timer mRefreshTimer = null;
    private ImageButton mPlayPauseButton;
    private ImageButton mRepeatButton;
    private ImageButton mRandomButton;

    private final static String TAG = "OdysseyNowPlayingFragment";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        // set visibility of quickcontrols
        ((MainActivity) getActivity()).getQuickControl().setVisibility(View.GONE);

        View rootView = inflater.inflate(R.layout.fragment_now_playing, container, false);

        mTitleTextView = (TextView) rootView.findViewById(R.id.nowPlayingTitleView);

        mAlbumTextView = (TextView) rootView.findViewById(R.id.nowPlayingAlbumView);

        mArtistTextView = (TextView) rootView.findViewById(R.id.nowPlayingArtistView);

        mCoverImageView = (ImageView) rootView.findViewById(R.id.nowPlayingAlbumImageView);

        mMinDuration = (TextView) rootView.findViewById(R.id.nowPlayingMinValue);

        mMinDuration.setText("0:00");

        mMaxDuration = (TextView) rootView.findViewById(R.id.nowPlayingMaxValue);

        mSeekBar = (SeekBar) rootView.findViewById(R.id.nowPlayingSeekBar);

        // set listener for seekbar
        mSeekBar.setOnSeekBarChangeListener(this);

        // get the playbackservice
        mServiceConnection = new PlaybackServiceConnection(getActivity().getApplicationContext());
        mServiceConnection.setNotifier(new ServiceConnectionListener());
        mServiceConnection.openConnection();

        // Set up button listeners
        rootView.findViewById(R.id.nowPlayingNextButton).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                try {
                    mServiceConnection.getPBS().next();
                } catch (RemoteException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        });

        rootView.findViewById(R.id.nowPlayingPreviousButton).setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View arg0) {
                try {
                    mServiceConnection.getPBS().previous();
                } catch (RemoteException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        });

        mPlayPauseButton = (ImageButton) rootView.findViewById(R.id.nowPlayingPlaypauseButton);

        mPlayPauseButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View arg0) {
                try {
                    mServiceConnection.getPBS().togglePause();
                } catch (RemoteException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        });

        rootView.findViewById(R.id.nowPlayingStopButton).setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View arg0) {
                try {
                    mServiceConnection.getPBS().stop();
                } catch (RemoteException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        });

        // TODO change repeat behavior to toggle track, playlist, nothing
        mRepeatButton = (ImageButton) rootView.findViewById(R.id.nowPlayingRepeatButton);

        mRepeatButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View arg0) {
                try {
                    int repeat = (mServiceConnection.getPBS().getRepeat() == REPEATSTATE.REPEAT_ALL.ordinal()) ? REPEATSTATE.REPEAT_OFF.ordinal() : REPEATSTATE.REPEAT_ALL.ordinal();

                    mServiceConnection.getPBS().setRepeat(repeat);
                    if (mServiceConnection.getPBS().getRepeat() == REPEATSTATE.REPEAT_ALL.ordinal()) {
                        mRepeatButton.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_repeat_white));
                    } else {
                        mRepeatButton.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_repeat));
                    }
                } catch (RemoteException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        });

        // TODO change random behavior
        mRandomButton = (ImageButton) rootView.findViewById(R.id.nowPlayingRandomButton);

        mRandomButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View arg0) {
                try {
                    int random = (mServiceConnection.getPBS().getRandom() == RANDOMSTATE.RANDOM_ON.ordinal()) ? RANDOMSTATE.RANDOM_OFF.ordinal() : RANDOMSTATE.RANDOM_ON.ordinal();

                    mServiceConnection.getPBS().setRandom(random);
                    if (mServiceConnection.getPBS().getRandom() == RANDOMSTATE.RANDOM_ON.ordinal()) {
                        mRandomButton.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_shuffle_white));
                    } else {
                        mRandomButton.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_shuffle));
                    }
                } catch (RemoteException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        });

        getActivity().getApplicationContext().registerReceiver(new NowPlayingReceiver(), new IntentFilter(PlaybackService.MESSAGE_NEWTRACKINFORMATION));

        return rootView;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mRefreshTimer != null) {
            mRefreshTimer.cancel();
            mRefreshTimer.purge();
            mRefreshTimer = null;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // get the playbackservice
        mServiceConnection = new PlaybackServiceConnection(getActivity().getApplicationContext());
        mServiceConnection.setNotifier(new ServiceConnectionListener());
        mServiceConnection.openConnection();

    }

    private void updateStatus() {

        // get current track
        TrackItem currentTrack = null;
        try {
            currentTrack = mServiceConnection.getPBS().getCurrentSong();
        } catch (RemoteException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        if (currentTrack == null) {
            currentTrack = new TrackItem();
        }
        Log.v(TAG, "Current track: " + currentTrack);
        // set tracktitle, album, artist and albumcover
        mTitleTextView.setText(currentTrack.getTrackTitle());

        mAlbumTextView.setText(currentTrack.getTrackAlbum());

        mArtistTextView.setText(currentTrack.getTrackArtist());

        String where = android.provider.MediaStore.Audio.Albums.ALBUM_KEY + "=?";

        String whereVal[] = { currentTrack.getTrackAlbumKey() };

        Cursor cursor = getActivity().getContentResolver().query(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, new String[] { MediaStore.Audio.Albums.ALBUM_ART }, where, whereVal, "");

        String coverPath = null;
        if (cursor.moveToFirst()) {
            coverPath = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Albums.ALBUM_ART));
        }

        cursor.close();

        if (coverPath != null) {
            // create and execute new asynctask
            AsyncLoader.CoverViewHolder coverHolder = new AsyncLoader.CoverViewHolder();
            coverHolder.coverViewReference = new WeakReference<ImageView>(mCoverImageView);
            coverHolder.imagePath = coverPath;
            coverHolder.task = new AsyncLoader();

            coverHolder.task.execute(coverHolder);
        } else {
            mCoverImageView.setImageResource(R.drawable.coverplaceholder);
        }

        // calculate duration in minutes and seconds
        String seconds = String.valueOf((currentTrack.getTrackDuration() % 60000) / 1000);

        String minutes = String.valueOf(currentTrack.getTrackDuration() / 60000);

        if (seconds.length() == 1) {
            mMaxDuration.setText(minutes + ":0" + seconds);
        } else {
            mMaxDuration.setText(minutes + ":" + seconds);
        }

        // set up seekbar
        mSeekBar.setMax((int) currentTrack.getTrackDuration());

        updateSeekBar();

        updateDurationView();

        try {
            final boolean isRandom = mServiceConnection.getPBS().getRandom() == 1 ? true : false;
            final boolean songPlaying = mServiceConnection.getPBS().getPlaying() == 1 ? true : false;
            final boolean isRepeat = mServiceConnection.getPBS().getRepeat() == 1 ? true : false;
            Activity activity = (Activity) getActivity();
            if (activity != null) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // update imagebuttons
                        if (songPlaying) {
                            mPlayPauseButton.setImageDrawable(getResources().getDrawable(android.R.drawable.ic_media_pause));
                        } else {
                            mPlayPauseButton.setImageDrawable(getResources().getDrawable(android.R.drawable.ic_media_play));
                        }
                        if (isRepeat) {
                            mRepeatButton.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_repeat_white));
                        } else {
                            mRepeatButton.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_repeat));
                        }
                        if (isRandom) {
                            mRandomButton.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_shuffle_white));
                        } else {
                            mRandomButton.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_shuffle));
                        }

                    }
                });
            }

        } catch (RemoteException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void updateSeekBar() {
        try {
            mSeekBar.setProgress(mServiceConnection.getPBS().getTrackPosition());
        } catch (RemoteException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void updateDurationView() {
        // calculate duration in minutes and seconds
        String seconds = "";
        String minutes = "";
        try {
            seconds = String.valueOf((mServiceConnection.getPBS().getTrackPosition() % 60000) / 1000);
            minutes = String.valueOf(mServiceConnection.getPBS().getTrackPosition() / 60000);
        } catch (RemoteException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        if (seconds.length() == 1) {
            mMinDuration.setText(minutes + ":0" + seconds);
        } else {
            mMinDuration.setText(minutes + ":" + seconds);
        }
    }

    private class RefreshTask extends TimerTask {

        @Override
        public void run() {
            Activity activity = (Activity) getActivity();
            if (activity != null) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateDurationView();
                        updateSeekBar();
                    }
                });
            }

        }
    };

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

        if (fromUser) {
            try {
                mServiceConnection.getPBS().seekTo(progress);
            } catch (RemoteException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        // TODO Auto-generated method stub

    }

    private class ServiceConnectionListener implements PlaybackServiceConnection.ConnectionNotifier {

        @Override
        public void onConnect() {
            Log.v(TAG, "Service connection established");
            updateStatus();
            if (mRefreshTimer != null) {
                mRefreshTimer.cancel();
                mRefreshTimer.purge();
                mRefreshTimer = null;
            }
            mRefreshTimer = new Timer();
            mRefreshTimer.scheduleAtFixedRate(new RefreshTask(), 0, 500);
        }

        @Override
        public void onDisconnect() {
            // TODO Auto-generated method stub

        }

    }

    private class NowPlayingReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(PlaybackService.MESSAGE_NEWTRACKINFORMATION)) {
                Log.v(TAG, "Received new information");
                // Extract nowplaying info
                ArrayList<NowPlayingInformation> infoArray = intent.getExtras().getParcelableArrayList(PlaybackService.INTENT_NOWPLAYINGNAME);
                if (infoArray.size() != 0) {
                    NowPlayingInformation info = infoArray.get(0);
                    final boolean songPlaying = (info.getPlaying() == 1) ? true : false;
                    final boolean isRepeat = (info.getRepeat() == REPEATSTATE.REPEAT_ALL.ordinal()) ? true : false;
                    final boolean isRandom = (info.getRandom() == RANDOMSTATE.RANDOM_ON.ordinal()) ? true : false;

                    new Thread() {
                        public void run() {
                            Activity activity = (Activity) getActivity();
                            if (activity != null) {
                                activity.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        // update imagebuttons
                                        if (songPlaying) {
                                            mPlayPauseButton.setImageDrawable(getResources().getDrawable(android.R.drawable.ic_media_pause));
                                        } else {
                                            mPlayPauseButton.setImageDrawable(getResources().getDrawable(android.R.drawable.ic_media_play));
                                        }
                                        if (isRepeat) {
                                            mRepeatButton.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_repeat_white));
                                        } else {
                                            mRepeatButton.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_repeat));
                                        }
                                        if (isRandom) {
                                            mRandomButton.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_shuffle_white));
                                        } else {
                                            mRandomButton.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_shuffle));
                                        }
                                        // update views
                                        updateStatus();
                                    }
                                });
                            }
                        }
                    }.start();
                }
            }
        }

    }
}
