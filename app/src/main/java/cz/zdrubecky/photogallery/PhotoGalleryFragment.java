package cz.zdrubecky.photogallery;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

// Extend VisibleFragment to be able to manage broadcasts
public class PhotoGalleryFragment extends VisibleFragment {
    private static final String TAG = "PhotoGalleryFragment";
    private static final int PAGE_SIZE = 100;

    private RecyclerView mPhotoRecyclerView;
    private List<GalleryItem> mItems = new ArrayList<>();
    private static int mCurrentPage;
    // The generic arg is set right here and is inferred from further on
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
        setHasOptionsMenu(true);

        mCurrentPage = 1;
        QueryPreferences.setCurrentPage(getActivity(), mCurrentPage);

        // Suck the data!
        updateItems();

        // This fragment's handler, it will attach automatically to the caller
        Handler responseHandler = new Handler();
        mThumbnailDownloader = new ThumbnailDownloader<>(responseHandler);
        mThumbnailDownloader.setThumbnailDownloadListener(
                // Instantiate a new anonymous class from the interface (this is the only scenario where it's possible)
                new ThumbnailDownloader.ThumbnailDownloadListener<PhotoHolder>() {
                    // Notice the target still being here, appropriately
                    @Override
                    public void onThumbnailDownloaded(PhotoHolder target, Bitmap thumbnail) {
                        // Create drawable from Bitmap
                        Drawable drawable = new BitmapDrawable(getResources(), thumbnail);
                        // Finally, bind the thing
                        target.bindDrawable(drawable);
                    }
                }
        );
        mThumbnailDownloader.start();
        // Beware of a race condition, so make sure that the guts are ready for us so we call the looper right away
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
                    QueryPreferences.setCurrentPage(getActivity(), mCurrentPage);
                    Log.i(TAG, "Fetching a new page.");

                    updateItems();
                }
            }
        });

        return v;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.fragment_photo_gallery, menu);

        // Get the item directly from the menu, the view will follow (API 11 allowed this)
        final MenuItem searchItem = menu.findItem(R.id.menu_item_search);
        // Invalid call (well, partially - it's invincible)
//        final SearchView searchView = (SearchView) searchItem.getActionView();
        // The correct call
        final SearchView searchView = (SearchView) MenuItemCompat.getActionView(searchItem);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                Log.d(TAG, "onQueryTextSubmit: " + query);
                QueryPreferences.setStoredQuery(getActivity(), query);

                // Reset the page counter
                mCurrentPage = 1;
                QueryPreferences.setCurrentPage(getActivity(), mCurrentPage);

                updateItems();

                // All of these did not work thanks to the compat menu
//                searchItem.collapseActionView();
//                MenuItemCompat.collapseActionView(searchItem);
//                getActivity().invalidateOptionsMenu();
                searchView.onActionViewCollapsed();

                // Get the currently focused view (soft keyboard)
                View view = getActivity().getCurrentFocus();
                if (view != null) {
                    // Get the system service and use it to hide the keyboard
                    InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                }

                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                Log.d(TAG, "onQueryTextChange: " + newText);
                return false;
            }
        });

        searchView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String query = QueryPreferences.getStoredQuery(getActivity());
                searchView.setQuery(query, false);
            }
        });

        MenuItem toggleItem = menu.findItem(R.id.menu_item_toggle_polling);
        if (PollService.isServiceAlarmOn(getActivity())) {
            toggleItem.setTitle(R.string.stop_polling);
        } else {
            toggleItem.setTitle(R.string.start_polling);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_item_clear:
                QueryPreferences.setStoredQuery(getActivity(), null);

                // Reset the page counter
                mCurrentPage = 1;
                QueryPreferences.setCurrentPage(getActivity(), mCurrentPage);

                updateItems();

                return true;
            case R.id.menu_item_toggle_polling:
                boolean shouldStartAlarm = !PollService.isServiceAlarmOn(getActivity());
                PollService.setServiceAlarm(getActivity(), shouldStartAlarm);
                // Tell the parent activity to refresh its menu
                getActivity().invalidateOptionsMenu();

                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    // Catch the screen rotation here, cause the fragment is retained and is not destroyed
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mThumbnailDownloader.clearQueue();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mThumbnailDownloader.quit();
        Log.i(TAG, "Background thread destroyed.");
    }

    private void updateItems() {
        String query = QueryPreferences.getStoredQuery(getActivity());

        new FetchItemsTask(query).execute();
    }

    private void setupAdapter() {
        // Check if the fragment is added to its activity (it receives callbacks from it)
        // We have to do this because of the background thread working
        if (isAdded()) {
            PhotoAdapter adapter = (PhotoAdapter) mPhotoRecyclerView.getAdapter();

            if (adapter != null) {
                // Notify the adapter that the mItems list has changed so it can adjust
                adapter.setItems(mItems);
                adapter.notifyDataSetChanged();

                Log.i(TAG, "Adapter's items count: " + adapter.getItemCount());

                if (mCurrentPage > 1) {
                    LinearLayoutManager manager = (LinearLayoutManager) mPhotoRecyclerView.getLayoutManager();

                    // Move the position up one item
                    manager.scrollToPosition(manager.findLastCompletelyVisibleItemPosition() + 1);
                }
            } else {
                Log.i(TAG, "Setting up a new adapter...");
                mPhotoRecyclerView.setAdapter(new PhotoAdapter(mItems));
            }
        }
    }

    // Let's run the task in a background thread and publish the results in the UI Thread
    // Prevents the ANR (app not responding), which is caught by an Android watchdog
    // It wraps around Thread and Handler classes and is used for short operations
    // ...therefore we use the handler for the image downloading instead (furthermore, it's not really async since version 3.2)
    // The generic params are: params, progress and result
    // Steps are: onPreExecute, doInBackground, onProgressUpdate and onPostExecute.
    // "Loaders" are an alternative to this if we don't want to manage the asynctask lifecycle
    private class FetchItemsTask extends AsyncTask<Void, Void, List<GalleryItem>> {
        private String mQuery;

        public FetchItemsTask(String query) {
            mQuery = query;
        }

        // String... params would receive variable amount of strings
        @Override
        protected List<GalleryItem> doInBackground(Void... voids) {
            if (mQuery == null) {
                return new FlickrFetchr().fetchRecentPhotos(mCurrentPage);
            } else {
                return new FlickrFetchr().searchPhotos(mQuery, mCurrentPage);
            }
        }

        // This method is handled by the UI thread so it can update the UI safely
        // Doing this in the background would result in corrupted data
        // doInBackground output is onPostExecute input
        @Override
        protected void onPostExecute(List<GalleryItem> galleryItems) {
            Log.i(TAG, "mItems size before updating: " + mItems.size());
            if (mCurrentPage > 1) {
                mItems.addAll(galleryItems);
            } else {
                mItems = galleryItems;
            }
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

        // The adapter stores the reference to a memory space, where the given items are
        // If the parent items change its location, the reference has to be updated, hence the setItems method further down
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
            // Set the current holder as a target of the message
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
