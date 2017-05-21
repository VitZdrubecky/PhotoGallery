package cz.zdrubecky.photogallery;

import android.net.Uri;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FlickrFetchr {
    private static final String TAG = "FlickFetchr";
    private static final String API_KEY = "9a0554259914a86fb9e7eb014e4e5d52";
    private static final String FETCH_RECENTS_METHOD = "flickr.photos.getRecent";
    private static final String SEARCH_METHOD = "flickr.photos.search";
    // This builder escapes the params
    private static final Uri ENDPOINT =Uri.parse("https://api.flickr.com/services/rest/")
            .buildUpon()
            .appendQueryParameter("api_key", API_KEY)
            .appendQueryParameter("format", "json")
            .appendQueryParameter("nojsoncallback", "1")
            .appendQueryParameter("extras", "url_s")
            .build();

    public byte[] getUrlBytes(String urlSpec) throws IOException {
        URL url = new URL(urlSpec);
        // openConnection() returns a general connection, so we have to cast it to HTTP and gain access to the specific methods and codes
        // The connection waits to open an input/output stream
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        try {
            // Here's where we push the incoming data
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            InputStream in = connection.getInputStream();

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException(connection.getResponseMessage() + ": with " + urlSpec);
            }

            int bytesRead;
            byte[] buffer = new byte[1024];

            while ((bytesRead = in.read(buffer)) > 0) {
                out.write(buffer, 0, bytesRead);
            }

            out.close();

            return out.toByteArray();
        } finally {
            connection.disconnect();
        }
    }

    // Specific method, returning a string instead of, let's say, images
    public String getUrlString(String urlSpec) throws IOException {
        return new String(getUrlBytes(urlSpec));
    }

    public List<GalleryItem> fetchRecentPhotos(int page) {
        String url = buildUrl(FETCH_RECENTS_METHOD, null, page);

        return downloadGalleryItems(url);
    }

    public List<GalleryItem> searchPhotos(String query, int page) {
        String url = buildUrl(SEARCH_METHOD, query, page);

        return downloadGalleryItems(url);
    }

    public List<GalleryItem> downloadGalleryItems(String url) {
        List<GalleryItem> items = new ArrayList<>();

        try {
            String jsonString = getUrlString(url);

            Log.i(TAG, "Received JSON: " + jsonString);

            JSONObject jsonBody = new JSONObject(jsonString);
            // The old call
//            parseItems(items, jsonBody);
            items = parseItems(jsonBody);
        } catch (IOException ioe) {
            Log.e(TAG, "Failed to fetch items.", ioe);
        } catch (JSONException je) {
            Log.e(TAG, "Failed to parse JSON.", je);
        }

        return items;
    }

    private String buildUrl(String method, String query, int page) {
        Uri.Builder uriBuilder = ENDPOINT.buildUpon()
                .appendQueryParameter("method", method);

        if (method.equals(SEARCH_METHOD)) {
            uriBuilder.appendQueryParameter("text", query);
        }

        Log.i(TAG, "Getting the page no." + page);

        uriBuilder.appendQueryParameter("page", Integer.toString(page));

        return uriBuilder.build().toString();
    }

    private List<GalleryItem> parseItems(JSONObject jsonBody) throws IOException, JSONException {
        JSONObject photosJsonObject = jsonBody.getJSONObject("photos");
        String photoString = photosJsonObject.getString("photo");

        Gson gson = new GsonBuilder().create();

        // Parse the source as an array and then cast it to a list, ingenious
        // Arrays.asList returns a fixed sized list backed by an array, and you can't add elements to it!!! (which I did of course) CAST IT!
        return new ArrayList<>(Arrays.asList(gson.fromJson(photoString, GalleryItem[].class)));

        // Alternative WITHOUT the use of Gson
//        JSONArray photoJsonArray = photosJsonObject.getJSONArray("photo");
//        for (int i = 0; i < photoJsonArray.length(); i++) {
//            JSONObject photoJsonObject = photoJsonArray.getJSONObject(i);
//
//            GalleryItem item = new GalleryItem();
//            item.setId(photoJsonObject.getString("id"));
//            item.setCaption(photoJsonObject.getString("title"));
//
//            // See if there's a photo and throw the item away otherwise
//            if (!photoJsonObject.has("url_s")) {
//                continue;
//            }
//
//            item.setUrl(photoJsonObject.getString("url_s"));
//            items.add(item);
//        }
    }
}
