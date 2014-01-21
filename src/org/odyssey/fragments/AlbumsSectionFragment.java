package org.odyssey.fragments;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import org.odyssey.MusicLibraryHelper;
import org.odyssey.R;
import org.odyssey.manager.AsyncLoader;
import org.odyssey.manager.AsyncLoader.CoverViewHolder;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.util.LruCache;
import android.support.v4.widget.CursorAdapter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.SectionIndexer;
import android.widget.TextView;

public class AlbumsSectionFragment extends Fragment implements
		LoaderManager.LoaderCallbacks<Cursor>, OnItemClickListener {

	AlbumCursorAdapter mCursorAdapter;
	ArrayList<String> mSectionList;
	OnAlbumSelectedListener mAlbumSelectedCallback;

	private static final String TAG = "AlbumsSectionFragment";
	
	// Listener for communication via container activity
	public interface OnAlbumSelectedListener {
		public void onAlbumSelected(int position);
	}
	
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		
        // This makes sure that the container activity has implemented
        // the callback interface. If not, it throws an exception
        try {
        	mAlbumSelectedCallback = (OnAlbumSelectedListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnHeadlineSelectedListener");
        }
		
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View rootView = inflater.inflate(R.layout.fragment_albums, container,
				false);

		mCursorAdapter = new AlbumCursorAdapter(getActivity(), null, 0);

		GridView mainGridView = (GridView) rootView;

		mainGridView.setNumColumns(2);

		mainGridView.setAdapter(mCursorAdapter);
		
		mainGridView.setOnItemClickListener((OnItemClickListener) this);

		// Prepare loader ( start new one or reuse old)
		getLoaderManager().initLoader(0, null, this);

		return rootView;
	}

	private class AlbumCursorAdapter extends CursorAdapter implements
			SectionIndexer {

		private LayoutInflater mInflater;
		private Cursor mCursor;
		private LruCache<String, Drawable> mCache;

		public AlbumCursorAdapter(Context context, Cursor c, int flags) {
			super(context, c, flags);

			this.mInflater = LayoutInflater.from(context);
			this.mCursor = c;
			this.mCache = new LruCache<String, Drawable>(24);
		}

		@Override
		public void bindView(View view, Context context, Cursor cursor) {
			// placeholder
		}

		@Override
		public View newView(Context context, Cursor cursor, ViewGroup viewGroup) {

			// placeholder
			return null;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {

			Log.v(TAG, "Index: " + position);

			int coverIndex = 0;
			int labelIndex = 0;

			AsyncLoader.CoverViewHolder coverHolder = null;

			if (convertView == null) {

				convertView = mInflater.inflate(R.layout.item_albums, null);

				// create new coverholder for imageview(cover) and
				// textview(albumlabel)
				coverHolder = new AsyncLoader.CoverViewHolder();
				coverHolder.coverView = (ImageView) convertView
						.findViewById(R.id.imageViewAlbum);
				coverHolder.labelView = (TextView) convertView
						.findViewById(R.id.textViewAlbumItem);

				convertView.setTag(coverHolder);

			} else {
				// get coverholder from convertview and cancel asynctask
				coverHolder = (CoverViewHolder) convertView.getTag();
				if (coverHolder.task != null)
					coverHolder.task.cancel(true);
			}

			// set default cover

			// get imagepath and labeltext
			if (this.mCursor == null) {
				return convertView;
			}

			this.mCursor.moveToPosition(position);

			coverIndex = mCursor
					.getColumnIndex(MediaStore.Audio.Albums.ALBUM_ART);
			labelIndex = mCursor.getColumnIndex(MediaStore.Audio.Albums.ALBUM);

			if (labelIndex >= 0) {
				// coverHolder.labelText = mCursor.getString(labelIndex);
				String labelText = mCursor.getString(labelIndex);
				if (labelText != null) {
					coverHolder.labelView.setText(labelText);
				}
			} else {
				// placeholder for empty labels
				coverHolder.labelView.setText("");
			}

			// Check for valid column
			if (coverIndex >= 0) {
				// Get column value (Image-URL)
				coverHolder.imagePath = mCursor.getString(coverIndex);
				if (coverHolder.imagePath != null) {
					// Check cache first
					Drawable cacheImage = mCache.get(coverHolder.imagePath);
					if (cacheImage == null) {
						// Cache miss
						// create and execute new asynctask
						coverHolder.task = new AsyncLoader();
						coverHolder.cache = new WeakReference<LruCache<String, Drawable>>(
								mCache);
						coverHolder.task.execute(coverHolder);
					} else {
						// Cache hit
						coverHolder.coverView.setImageDrawable(cacheImage);
					}
				} else {
					// Cover entry has no album art
					coverHolder.coverView
							.setImageResource(R.drawable.coverplaceholder);
				}
			} else {
				coverHolder.coverView
						.setImageResource(R.drawable.coverplaceholder);
				coverHolder.imagePath = null;
			}
			return convertView;
		}

		@Override
		public Cursor swapCursor(Cursor c) {

			this.mCursor = c;

			if (mCursor == null) {
				return super.swapCursor(c);
			}

			// create sectionlist for fastscrolling

			mSectionList = new ArrayList<String>();

			this.mCursor.moveToPosition(0);

			char lastSection = this.mCursor.getString(
					this.mCursor.getColumnIndex(MediaStore.Audio.Albums.ALBUM))
					.charAt(0);

			mSectionList.add("" + lastSection);

			for (int i = 1; i < this.mCursor.getCount(); i++) {

				this.mCursor.moveToPosition(i);

				char currentSection = this.mCursor.getString(
						this.mCursor
								.getColumnIndex(MediaStore.Audio.Albums.ALBUM))
						.charAt(0);

				if (lastSection != currentSection) {
					mSectionList.add("" + currentSection);

					lastSection = currentSection;
				}

			}

			return super.swapCursor(c);
		}

		@Override
		public int getPositionForSection(int sectionIndex) {

			char section = mSectionList.get(sectionIndex).charAt(0);

			for (int i = 0; i < this.mCursor.getCount(); i++) {

				this.mCursor.moveToPosition(i);

				char currentSection = this.mCursor.getString(
						this.mCursor
								.getColumnIndex(MediaStore.Audio.Albums.ALBUM))
						.charAt(0);

				if (section == currentSection) {
					return i;
				}

			}

			return 0;
		}

		@Override
		public int getSectionForPosition(int pos) {

			this.mCursor.moveToPosition(pos);

			String albumName = this.mCursor.getString(this.mCursor
					.getColumnIndex(MediaStore.Audio.Albums.ALBUM));

			char albumSection = albumName.charAt(0);

			for (int i = 0; i < mSectionList.size(); i++) {

				if (albumSection == mSectionList.get(i).charAt(0)) {
					return i;
				}

			}

			return 0;
		}

		@Override
		public Object[] getSections() {

			return mSectionList.toArray();
		}

	}

	// New loader needed
	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle bundle) {
		return new CursorLoader(getActivity(),
				MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
				MusicLibraryHelper.projectionAlbums, "", null,
				MediaStore.Audio.Albums.ALBUM);
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
		mCursorAdapter.swapCursor(cursor);
	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
		mCursorAdapter.swapCursor(null);
	}
	
	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		// Send the event to the host activity
		mAlbumSelectedCallback.onAlbumSelected(position);
		
	}

}