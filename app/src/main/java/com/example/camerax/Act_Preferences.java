package com.example.camerax;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.transition.Fade;
import android.transition.Slide;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.example.camerax.preferences.Frg_Preferences;

public class Act_Preferences extends AppCompatActivity {

    public Act_Preferences() {
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        }
        setContentView(R.layout.act_preferences);
        new Handler(getMainLooper()).post(() -> {
            if (savedInstanceState == null) {
                navigateTo(Frg_Preferences.PreferenceScreens.PS_ROOT, false, getString(R.string.app_name));
            }
            findView();
        });
    }

    public void navigateTo(String key, boolean addToBackStack, String title) {
        Frg_Preferences frg = new Frg_Preferences();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            frg.setEnterTransition(new Slide());
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            frg.setEnterTransition(new Fade());
        }

        Bundle bundle = new Bundle();
        bundle.putString(Frg_Preferences.PREFERENCE_SCREEN_KEY, key);
        frg.setArguments(bundle);

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.add(R.id.root, frg);

        if (addToBackStack) {
            transaction.addToBackStack(key);
        }

        transaction.commit();
        setTitle(title);
    }

    private void findView() {
        Toolbar toolbar = findViewById(R.id.toolBar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.root);
        if (fragment != null) {
            setTitle(getString(R.string.app_name));
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return false;
        }
        return super.onOptionsItemSelected(item);
    }
}
