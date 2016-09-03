package arun.com.chromer.activities;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

import com.bumptech.glide.Glide;

import java.net.MalformedURLException;
import java.net.URL;

import arun.com.chromer.R;
import arun.com.chromer.customtabs.CustomTabs;
import arun.com.chromer.preferences.manager.Preferences;
import arun.com.chromer.shared.Constants;
import arun.com.chromer.util.Benchmark;
import arun.com.chromer.util.Util;
import de.jetwick.snacktory.HtmlFetcher;
import de.jetwick.snacktory.JResult;
import timber.log.Timber;

public class CustomTabActivity extends AppCompatActivity {
    private boolean isLoaded = false;
    private ExtractionTask mExtractionTask;

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getIntent() == null || getIntent().getData() == null) {
            Toast.makeText(this, getString(R.string.unsupported_link), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        final String url = getIntent().getDataString();
        final boolean isWebhead = getIntent().getBooleanExtra(Constants.EXTRA_KEY_FROM_WEBHEAD, false);
        final int color = getIntent().getIntExtra(Constants.EXTRA_KEY_WEBHEAD_COLOR, Constants.NO_COLOR);

        Benchmark.start("Custom tab launching in CTA");
        CustomTabs.from(this)
                .forUrl(url)
                .forWebHead(isWebhead)
                .overrideToolbarColor(color)
                .prepare()
                .launch();
        Benchmark.end();

        dispatchDescriptionTask();

        if (Preferences.aggressiveLoading(this)) {
            delayedGoToBack();
        }
    }

    private void delayedGoToBack() {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                moveTaskToBack(true);
            }
        }, 650);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void dispatchDescriptionTask() {
        if (Util.isLollipopAbove()) {
            final Intent intent = getIntent();
            final Bitmap icon = intent.getParcelableExtra(Constants.EXTRA_KEY_WEBHEAD_ICON);
            final String title = intent.getStringExtra(Constants.EXTRA_KEY_WEBHEAD_TITLE);
            if (title != null) {
                setTaskDescription(new ActivityManager.TaskDescription(title, icon));
            } else {
                mExtractionTask = new ExtractionTask(getIntent().getDataString());
                mExtractionTask.execute();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isLoaded) {
            // HACK
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mExtractionTask != null && !mExtractionTask.isCancelled()) {
            mExtractionTask.cancel(true);
        }
        mExtractionTask = null;
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        isLoaded = true;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private class ExtractionTask extends AsyncTask<Void, String, Void> {
        final String mUrl;
        String mTitle;
        Bitmap mIcon;

        ExtractionTask(@Nullable String url) {
            mUrl = url;
        }

        @Override
        protected void onPreExecute() {
            if (mUrl != null && mUrl.length() > 0) {
                setTaskDescription(new ActivityManager.TaskDescription(getString(R.string.loading)));
            }
        }

        @Override
        protected Void doInBackground(Void... params) {
            if (mUrl != null && mUrl.length() > 0) {
                Timber.d("Beginning extraction");
                try {
                    final HtmlFetcher fetcher = new HtmlFetcher();
                    final String unShortenedUrl = fetcher.unShortenUrl(mUrl);
                    final JResult res = fetcher.fetchAndExtract(unShortenedUrl, false);
                    mTitle = res.getTitle();

                    mIcon = Glide.with(CustomTabActivity.this)
                            .load(res.getFaviconUrl())
                            .asBitmap()
                            .into(-1, -1)
                            .get();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            String label = "";
            if (mTitle != null && mTitle.length() > 0) {
                label = mTitle;
            } else {
                try {
                    label = new URL(mUrl).getHost().toUpperCase();
                } catch (MalformedURLException ignored) {
                }
            }
            if (label.trim().length() == 0 && mUrl != null) {
                label = mUrl.toUpperCase();
            }
            Timber.d("Setting task description %s", label);
            setTaskDescription(new ActivityManager.TaskDescription(label, mIcon));
            mIcon = null;
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            Timber.d("Cancelled");
        }
    }
}
