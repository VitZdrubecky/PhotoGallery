package cz.zdrubecky.photogallery;

import android.content.Context;
import android.preference.PreferenceManager;

public class QueryPreferences {
    private static final String PREF_SEARCH_QUERY = "searchQuery";
    private static final String PREF_LAST_RESULT_ID = "lastResultId";
    private static final String PREF_CURRENT_PAGE = "currentPage";

    public static String getStoredQuery(Context context) {
        // General way of getting preferences, otherwise a specific context
        // This class has no context of its own, so it has to acquire one
        return PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_SEARCH_QUERY, null);
    }

    public static String getLastResultId(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_LAST_RESULT_ID, null);
    }

    public static int getCurrentPage(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getInt(PREF_CURRENT_PAGE, 1);
    }

    public static void setStoredQuery(Context context, String query) {
        // The editor allows storing multiple preferences at once
        // Writing happens in a background thread
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString(PREF_SEARCH_QUERY, query)
                .apply();
    }

    public static void setLastResultId(Context context, String lastResultId) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString(PREF_LAST_RESULT_ID, lastResultId)
                .apply();
    }

    public static void setCurrentPage(Context context, int currentPage) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putInt(PREF_CURRENT_PAGE, currentPage)
                .apply();
    }
}
