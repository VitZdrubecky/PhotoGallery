package cz.zdrubecky.photogallery;

import android.os.Bundle;
import android.support.annotation.LayoutRes;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;

// FragmentActivity lets me use the support library fragments, AppCompatActivity is its subclass and gives us a toolbar!
public abstract class SingleFragmentActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getLayoutResId());

        FragmentManager fm = getSupportFragmentManager();
        Fragment fragment = fm.findFragmentById(R.id.fragment_container);

        if (fragment == null) {
            fragment = createFragment();

            // The first method returns a fragment transaction, which can used for chaining
            fm.beginTransaction()
                .add(R.id.fragment_container, fragment)
                .commit();
        }
    }

    // Let the subclasses return their own layouts instead of this default one
    @LayoutRes
    protected int getLayoutResId() {
        return R.layout.activity_fragment;
    }

    // Returns the specific fragment to be added to the manager
    protected abstract Fragment createFragment();
}
