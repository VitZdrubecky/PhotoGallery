package cz.zdrubecky.photogallery;

import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class PhotoGalleryFragment extends Fragment {
    private static final String TAG = "PhotoGalleryFragment";
    private static final int PAGE_SIZE = 100;

    private RecyclerView mPhotoRecyclerView;
    private List<GalleryItem> mItems = new ArrayList<>();
    private int mCurrentPage;
    private ThumbnailDownloader<PhotoHolder> mThumbnailDownloader;

    public static PhotoGalleryFragment newInstance() {
        return new PhotoGalleryFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Retained fragment does not download data after every config change
        // The other possibility would be cancelling the asynctask when in need
        setRetainInstance(true);

        mCurrentPage = 1;

        // Suck the data!
        new FetchItemsTask().execute();

        mThumbnailDownloader = new ThumbnailDownloader<>();
        mThumbnailDownloader.start();
        // Beware of a race condition, so make sure that the guts are ready for us
        mThumbnailDownloader.getLooper();
        Log.i(TAG, "Background thread started.");
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_photo_gallery, container, false);

        mPhotoRecyclerView = (RecyclerView) v.findViewById(R.id.fragment_photo_gallery_recycler_view);

        setupAdapter();
        Log.i(TAG, "Adapter is set, continue with setting a layout manager...");
        mPhotoRecyclerView.setLayoutManager(new GridLayoutManager(getActivity(), 3));
        mPhotoRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();

                // Keep checking if the the recyclerview is at the end of the page
                if ((manager.findLastCompletelyVisibleItemPosition() + 1) == (mCurrentPage * PAGE_SIZE)) {
                    mCurrentPage++;
                    Log.i(TAG, "Fetching a new page.");

                    new FetchItemsTask().execute();
                }
            }
        });

        return v;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mThumbnailDownloader.quit();
        Log.i(TAG, "Background thread destroyed.");
    }

    private void setupAdapter() {
        // Check if the fragment is added to its activity (it receives callbacks from it)
        // We have to do this because of the background thread working
        if (isAdded()) {
            PhotoAdapter adapter = (PhotoAdapter) mPhotoRecyclerView.getAdapter();

            if (adapter != null) {
                // Notify the adapter that the mItems list has changed so it can adjust
                adapter.notifyDataSetChanged();
                LinearLayoutManager manager = (LinearLayoutManager) mPhotoRecyclerView.getLayoutManager();

                // Move the position up one item
                manager.scrollToPosition(manager.findLastCompletelyVisibleItemPosition() + 1);
            } else {
                Log.i(TAG, "Setting up a new adapter...");
                mPhotoRecyclerView.setAdapter(new PhotoAdapter(mItems));
            }
        }
    }

    // Let's run the task in a background thread and publish the results in the UI Thread
    // Prevents the ANR (app not responding), which is caught by an Android watchdog
    // It wraps around Thread and Handler classes and is used for short operations
    // The generic params are: params, progress and result
    // Steps are: onPreExecute, doInBackground, onProgressUpdate and onPostExecute.
    // "Loaders" are an alternative to this if we don't want to manage the asynctask lifecycle
    private class FetchItemsTask extends AsyncTask<Void, Void, List<GalleryItem>> {
        // String... params would receive variable amount of strings
        @Override
        protected List<GalleryItem> doInBackground(Void... voids) {
            return new FlickrFetchr().fetchItems(mCurrentPage);
        }

        // This method is handled by the UI thread so it can update the UI safely
        // Doing this in the background would result in corrupted data
        // doInBackground output is onPostExecute input
        @Override
        protected void onPostExecute(List<GalleryItem> galleryItems) {
            Log.i(TAG, "mItems size before updating: " + mItems.size());
            mItems.addAll(galleryItems);
            Log.i(TAG, "mItems size after updating: " + mItems.size());

            setupAdapter();
        }
    }

    private class PhotoHolder extends RecyclerView.ViewHolder {
        private ImageView mItemImageView;

        public PhotoHolder(View itemView) {
            super(itemView);

            mItemImageView = (ImageView) itemView.findViewById(R.id.fragment_photo_gallery_image_view);
        }

        public void bindDrawable(Drawable drawable) {
            mItemImageView.setImageDrawable(drawable);
        }
    }

    private class PhotoAdapter extends RecyclerView.Adapter<PhotoHolder> {
        private List<GalleryItem> mItems;

        public PhotoAdapter(List<GalleryItem> items) {
            mItems = items;
        }

        @Override
        public PhotoHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            // Create the view without the need of a layout
//            TextView textView = new TextView(getActivity());
            LayoutInflater inflater = LayoutInflater.from(getActivity());
            View view = inflater.inflate(R.layout.gallery_item, parent, false);

            return new PhotoHolder(view);
        }

        @Override
        public void onBindViewHolder(PhotoHolder holder, int position) {
            GalleryItem item = mItems.get(position);
            Drawable placeholder = getResources().getDrawable(R.drawable.bill_up_close);
            holder.bindDrawable(placeholder);
            // Set the current holder as a target
            mThumbnailDownloader.queueThumbnail(holder, item.getUrl());
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }

        public void setItems(List<GalleryItem> items) {
            mItems = items;
        }
    }
}
