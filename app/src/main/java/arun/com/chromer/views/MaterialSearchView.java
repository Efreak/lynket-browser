package arun.com.chromer.views;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.UiThread;
import android.support.v4.content.ContextCompat;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.mikepenz.community_material_typeface_library.CommunityMaterial;
import com.mikepenz.iconics.IconicsDrawable;

import java.util.List;

import arun.com.chromer.R;
import arun.com.chromer.search.SearchSuggestions;
import arun.com.chromer.util.Util;

public class MaterialSearchView extends FrameLayout implements SearchSuggestions.SuggestionsCallback {
    @ColorInt
    private final int mNormalColor = ContextCompat.getColor(getContext(), R.color.accent_icon_nofocus);
    @ColorInt
    private final int mFocusedColor = ContextCompat.getColor(getContext(), R.color.accent);

    private boolean mInFocus = false;
    private boolean mShouldClearText;

    private ImageView mMenuIconView;
    private ImageView mVoiceIconView;
    private TextView mLabel;
    private EditText mEditText;

    private IconicsDrawable mXIcon;
    private IconicsDrawable mVoiceIcon;
    private IconicsDrawable mMenuIcon;

    private VoiceIconClickListener mVoiceClickListener;

    private SearchSuggestions mSearchSuggestions;
    private Toast mToast;

    public MaterialSearchView(Context context) {
        super(context);
        init(context);
    }

    public MaterialSearchView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public MaterialSearchView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    private void init(Context context) {
        mSearchSuggestions = new SearchSuggestions(context, this);
        mXIcon = new IconicsDrawable(context)
                .icon(CommunityMaterial.Icon.cmd_close)
                .color(mNormalColor)
                .sizeDp(16);
        mVoiceIcon = new IconicsDrawable(context)
                .icon(CommunityMaterial.Icon.cmd_microphone)
                .color(mNormalColor)
                .sizeDp(18);
        mMenuIcon = new IconicsDrawable(context)
                .icon(CommunityMaterial.Icon.cmd_menu)
                .color(mNormalColor)
                .sizeDp(18);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        addView(LayoutInflater.from(getContext()).inflate(R.layout.material_search_view, this, false));

        mEditText = (EditText) findViewById(R.id.msv_edittext);
        mEditText.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                performClick();
            }
        });
        mEditText.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) gainFocus();
                else loseFocus();
            }
        });
        mEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                handleVoiceIconState();

                if (s.length() != 0) {
                    mLabel.setAlpha(0f);
                    mSearchSuggestions.fetchForQuery(s.toString());
                } else mLabel.setAlpha(0.5f);
            }
        });

        mMenuIconView = (ImageView) findViewById(R.id.msv_left_icon);
        mMenuIconView.setImageDrawable(mMenuIcon);

        mVoiceIconView = (ImageView) findViewById(R.id.msv_right_icon);
        mVoiceIconView.setImageDrawable(mVoiceIcon);
        mVoiceIconView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mShouldClearText) {
                    mEditText.setText("");
                    loseFocus();
                } else {
                    if (mVoiceClickListener != null) mVoiceClickListener.onClick();
                }
            }
        });

        mLabel = (TextView) findViewById(R.id.msv_label);
        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mInFocus) gainFocus();
            }
        });
    }

    private void gainFocus() {
        float labelAlpha = mEditText.getText().length() == 0 ? 0.5f : 0f;
        final AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(
                ObjectAnimator.ofFloat(mLabel, "alpha", labelAlpha),
                ObjectAnimator.ofFloat(mEditText, "alpha", 1).setDuration(300)
        );
        hardwareLayers();
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                clearLayerTypes();
                handleVoiceIconState();
                setFocusedColor();
            }
        });
        animatorSet.start();

        mInFocus = true;
    }

    private void loseFocus() {
        final AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(
                ObjectAnimator.ofFloat(mLabel, "alpha", 1),
                ObjectAnimator.ofFloat(mEditText, "alpha", 0).setDuration(300)
        );
        hardwareLayers();
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mEditText.clearFocus();
                setNormalColor();
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                clearLayerTypes();
                hideKeyboard();
            }
        });
        animatorSet.start();
        mInFocus = false;
    }

    private void clearLayerTypes() {
        mLabel.setLayerType(LAYER_TYPE_NONE, null);
        mEditText.setLayerType(LAYER_TYPE_NONE, null);
    }

    private void hardwareLayers() {
        mLabel.setLayerType(LAYER_TYPE_HARDWARE, null);
        mEditText.setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    private void hideKeyboard() {
        ((InputMethodManager) getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(getWindowToken(), 0);
    }

    private void setFocusedColor() {
        mMenuIconView.setImageDrawable(mMenuIcon.color(mFocusedColor));
        mVoiceIconView.setImageDrawable(mVoiceIcon.color(mFocusedColor));
    }

    private void setNormalColor() {
        mMenuIconView.setImageDrawable(mMenuIcon.color(mNormalColor));
        mVoiceIconView.setImageDrawable(mVoiceIcon.color(mNormalColor));
    }

    private void handleVoiceIconState() {
        if (mEditText.getText() != null && mEditText.getText().length() != 0) {
            if (!mShouldClearText) {
                if (mInFocus)
                    mVoiceIconView.setImageDrawable(mXIcon.color(mFocusedColor));
                else mVoiceIconView.setImageDrawable(mXIcon.color(mNormalColor));
            }
            mShouldClearText = true;
        } else {
            if (mShouldClearText) {
                if (mInFocus)
                    mVoiceIconView.setImageDrawable(mVoiceIcon.color(mFocusedColor));
                else mVoiceIconView.setImageDrawable(mVoiceIcon.color(mNormalColor));
            }
            mShouldClearText = false;
        }
    }

    @Override
    public void clearFocus() {
        loseFocus();
        View view = findFocus();
        if (view != null) view.clearFocus();
        super.clearFocus();
    }

    @Override
    public boolean hasFocus() {
        return mInFocus && super.hasFocus();
    }

    public void setOnSearchPerformedListener(@NonNull final SearchListener listener) {
        mEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    listener.onSearchPerformed(getURL());
                    return true;
                }
                return false;
            }
        });
    }

    @NonNull
    public String getText() {
        return mEditText.getText() == null ? "" : mEditText.getText().toString();
    }

    @NonNull
    public String getURL() {
        return Util.getSearchUrl(getText());
    }

    public void setVoiceIconClickListener(VoiceIconClickListener listener) {
        mVoiceClickListener = listener;
    }

    @Override
    public void setOnClickListener(OnClickListener l) {
        // no op
    }

    public void setOnMenuClickListener(OnClickListener listener) {
        mMenuIconView.setOnClickListener(listener);
    }

    @UiThread
    @Override
    public void onFetchSuggestions(@NonNull List<String> suggestions) {
        /*if (mToast != null) {
            mToast.cancel();
        }
        mToast = Toast.makeText(getContext(), suggestions.toString(), Toast.LENGTH_SHORT);
        mToast.show();*/
    }

    public interface VoiceIconClickListener {
        void onClick();
    }

    public interface SearchListener {
        void onSearchPerformed(@NonNull final String query);
    }
}
