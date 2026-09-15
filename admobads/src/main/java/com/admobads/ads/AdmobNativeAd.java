package com.admobads.ads;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;

import com.admobads.loading.skeleton.layout.SkeletonConstraintLayout;
import com.admobads.ads.utils.AdmobSdkGuard;
import com.google.android.libraries.ads.mobile.sdk.common.AdChoicesPlacement;
import com.google.android.libraries.ads.mobile.sdk.common.AdValue;
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError;
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView;
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd;
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdEventCallback;
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoader;
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback;
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest;
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class AdmobNativeAd {

    private static final String TAG = "AdNativeOnDemand";
    private final Activity ctx;
    private final FrameLayout nativeAdContainer;
    private final String id;
    private final Integer type;
    private String buttonColor;
    private String cta_btn_position = "";
    private Integer bodytextColor = 0;
    private Integer headingtextColor = 0;
    private Integer skeltonColor = 0;
    private Integer backgroundcolor = 0;
    private Integer marginstart = 0;
    private Integer marginend = 0;

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    private NativeAd currentNativeAd;

    public AdmobNativeAd(
            Activity ctx,
            FrameLayout nativeAdContainer,
            String id,
            Integer type,
            String buttonColor
    ) {
        this.ctx = ctx;
        this.nativeAdContainer = nativeAdContainer;
        this.id = id;
        this.type = type;
        this.buttonColor = buttonColor;

        this.nativeAdContainer.setPadding(4, 4, 4, 4);
    }

    public AdmobNativeAd setTextColor(
            Integer bodytextColor,
            Integer headingtextColor
    ) {
        this.bodytextColor = bodytextColor;
        this.headingtextColor = headingtextColor;
        return this;
    }

    public AdmobNativeAd setMargintoNative(
            Integer marginstart,
            Integer marginend
    ) {
        this.marginstart = marginstart;
        this.marginend = marginend;
        return this;
    }

    public AdmobNativeAd setSkeltonColor(Integer skeltonColor) {
        this.skeltonColor = skeltonColor;
        return this;
    }

    public AdmobNativeAd setCtaButtonPosition(String cta_btn_position) {
        this.cta_btn_position = cta_btn_position;
        return this;
    }

    public void load() {

        if (!AdmobSdkGuard.ensureInitialized("AdmobNativeAd.load")) {
            nativeAdContainer.removeAllViews();
            nativeAdContainer.setVisibility(View.GONE);
            return;
        }

        setupNativeContainer(type);

        List<NativeAd.NativeAdType> adTypes =
                Arrays.asList(NativeAd.NativeAdType.NATIVE);

        NativeAdRequest adRequest =
                new NativeAdRequest.Builder(id, adTypes)
                        .setAdChoicesPlacement(
                                AdChoicesPlacement.TOP_RIGHT
                        )
                        .build();

        NativeAdLoader.load(
                adRequest,
                new NativeAdLoaderCallback() {

                    @Override
                    public void onNativeAdLoaded(NativeAd nativeAd) {

                        Log.d(TAG, "Native ad loaded");

                        runOnMainThread(() -> {

                            // Destroy previous ad if one exists.
                            destroyCurrentAd();

                            currentNativeAd = nativeAd;

                            displayNativeAd(nativeAd);
                        });
                    }

                    @Override
                    public void onAdFailedToLoad(
                            LoadAdError loadAdError
                    ) {
                        Log.e(
                                TAG,
                                "Native ad failed: "
                                        + loadAdError.getMessage()
                        );

                        Log.e(TAG,
                                "Native ad failed"
                                        + "\ncode=" + loadAdError.getCode()
                                        + "\nmessage=" + loadAdError.getMessage()
                                        + "\nresponseInfo=" + loadAdError.getResponseInfo()
                        );

                        runOnMainThread(() -> {

                            // Optional:
                            // Hide container when loading fails.
                            //
                            // Uncomment if desired.
                            //
                            // nativeAdContainer.removeAllViews();
                            // nativeAdContainer.setVisibility(View.GONE);
                        });
                    }
                }
        );
    }

    private void setupNativeContainer(Integer adType) {

        nativeAdContainer.removeAllViews();
        nativeAdContainer.setVisibility(View.VISIBLE);

        View loadingView = getloadingtype(adType);

        if (skeltonColor != null && skeltonColor != 0) {

            SkeletonConstraintLayout skeletonLayout =
                    loadingView.findViewById(R.id.skeletonLayout);

            if (skeletonLayout != null) {
                skeletonLayout.setSkeletonColor(skeltonColor);
            }
        }

        if (backgroundcolor != null && backgroundcolor != 0) {

            View skeletonLayout =
                    loadingView.findViewById(R.id.skeletonLayout);

            if (skeletonLayout != null) {
                skeletonLayout.setBackgroundColor(
                        ctx.getResources().getColor(backgroundcolor)
                );
            }
        }

        DisplayMetrics displayMetrics =
                new DisplayMetrics();

        ctx.getWindowManager()
                .getDefaultDisplay()
                .getMetrics(displayMetrics);

        int screenWidth = displayMetrics.widthPixels;

        Resources resources = ctx.getResources();

        float density =
                resources.getDisplayMetrics().density;

        int startMarginPx =
                (int) (safeInt(marginstart) * density);

        int endMarginPx =
                (int) (safeInt(marginend) * density);

        int containerWidth =
                screenWidth -
                        (startMarginPx + endMarginPx);

        if (containerWidth < 0) {
            containerWidth = screenWidth;
        }

        FrameLayout.LayoutParams containerParams =
                (FrameLayout.LayoutParams)
                        nativeAdContainer.getLayoutParams();

        if (containerParams == null) {

            containerParams =
                    new FrameLayout.LayoutParams(
                            containerWidth,
                            FrameLayout.LayoutParams.WRAP_CONTENT
                    );

        } else {

            containerParams.width = containerWidth;
        }

        containerParams.gravity =
                Gravity.CENTER_HORIZONTAL;

        nativeAdContainer.setLayoutParams(containerParams);

        FrameLayout.LayoutParams loadingViewParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                );

        nativeAdContainer.addView(
                loadingView,
                loadingViewParams
        );

        nativeAdContainer.setBackgroundColor(
                Color.TRANSPARENT
        );
    }


    private void displayNativeAd(NativeAd nativeAd) {

        nativeAd.setAdEventCallback(
                new NativeAdEventCallback() {
                    @Override
                    public void onAdPaid(AdValue adValue) {
                        AdRevenueTracker.handlePaid(
                                adValue,
                                id,
                                AdRevenueTracker.FORMAT_NATIVE,
                                TAG
                        );
                    }
                }
        );

        NativeAdView nativeView =
                createNativeView();

        bindNativeAd(
                nativeAd,
                nativeView
        );

        nativeAdContainer.removeAllViews();

        ViewParent parent = nativeView.getParent();

        if (parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(nativeView);
        }


        nativeAdContainer.addView(
                nativeView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                )
        );

        nativeAdContainer.setVisibility(View.VISIBLE);
    }

    private NativeAdView createNativeView() {

        AdType adType =
                AdType.fromInt(type);

        int layoutId;

        switch (adType) {

            case BANNER:

                if (Objects.equals(
                        cta_btn_position,
                        "top"
                )) {

                    layoutId =
                            R.layout.tlib_banner_variant_one;

                } else {

                    layoutId =
                            R.layout.tlib_banner_variant_one;
                }

                break;

            case SMALL:
            case ADAPTIVE:

                if (Objects.equals(
                        cta_btn_position,
                        "top"
                )) {

                    layoutId =
                            R.layout.tlib_large_variant_one_top;

                } else {

                    layoutId =
                            R.layout.tlib_adaptive_variant_one_bottom;
                }

                break;

            case LARGE:

                if (Objects.equals(
                        cta_btn_position,
                        "top"
                )) {

                    layoutId =
                            R.layout.tlib_large_variant_two_top;

                } else {

                    layoutId =
                            R.layout.tlib_large_variant_two_bottom;
                }

                break;

            case LARGE_1:

                layoutId =
                        R.layout.tlib_large_variant_one_bottom;

                break;

            case MEDIUM:
            default:

                if (Objects.equals(
                        cta_btn_position,
                        "top"
                )) {

                    layoutId =
                            R.layout.tlib_medium_variant_one_top;

                } else {

                    layoutId =
                            R.layout.tlib_medium_variant_one_bottom;
                }

                break;
        }

        FrameLayout tempParent =
                new FrameLayout(ctx);

        LayoutInflater.from(ctx)
                .inflate(
                        layoutId,
                        tempParent,
                        true
                );

        NativeAdView nativeAdView =
                tempParent.findViewById(
                        R.id.native_ad_view
                );

        if (nativeAdView == null) {

            throw new IllegalStateException(
                    "native_ad_view missing from layout: "
                            + layoutId
            );
        }

        tempParent.removeView(nativeAdView);

        return nativeAdView;
    }

    private void bindNativeAd(
            NativeAd nativeAd,
            NativeAdView adView
    ) {

        try {

            View headlineView =
                    adView.findViewById(R.id.primary);

            View bodyView =
                    adView.findViewById(R.id.body);

            View ctaView =
                    adView.findViewById(R.id.cta);

            View iconView =
                    adView.findViewById(R.id.icon);

            View starRatingView =
                    adView.findViewById(R.id.rating_bar);

            View mediaViewCandidate =
                    adView.findViewById(R.id.media_view);

            MediaView mediaView =
                    mediaViewCandidate instanceof MediaView
                            ? (MediaView) mediaViewCandidate
                            : null;

            if (headlineView != null) {
                adView.setHeadlineView(headlineView);
            }

            if (bodyView != null) {
                adView.setBodyView(bodyView);
            }

            if (ctaView != null) {
                adView.setCallToActionView(ctaView);
            }

            if (iconView != null) {
                adView.setIconView(iconView);
            }

            if (starRatingView != null) {
                adView.setStarRatingView(starRatingView);
            }

            if (headlineView instanceof TextView) {

                TextView headline =
                        (TextView) headlineView;

                headline.setText(
                        nativeAd.getHeadline()
                );

                headline.setSelected(true);

                if (headingtextColor != null
                        && headingtextColor != 0) {

                    headline.setTextColor(
                            headingtextColor
                    );
                }
            }

            if (bodyView instanceof TextView) {

                TextView body =
                        (TextView) bodyView;

                String bodyText =
                        nativeAd.getBody();

                if (bodyText != null
                        && !bodyText.trim().isEmpty()) {

                    bodyView.setVisibility(
                            View.VISIBLE
                    );

                    body.setText(bodyText);

                    if (bodytextColor != null
                            && bodytextColor != 0) {

                        body.setTextColor(
                                bodytextColor
                        );
                    }

                } else {

                    bodyView.setVisibility(
                            View.INVISIBLE
                    );
                }
            }

            if (iconView instanceof ImageView) {

                ImageView icon =
                        (ImageView) iconView;

                if (nativeAd.getIcon() != null) {

                    iconView.setVisibility(
                            View.VISIBLE
                    );

                    icon.setImageDrawable(
                            nativeAd
                                    .getIcon()
                                    .getDrawable()
                    );

                } else {

                    iconView.setVisibility(
                            View.GONE
                    );
                }
            }

            AdType adType =
                    AdType.fromInt(type);

            if (starRatingView != null) {

                if (adType == AdType.BANNER
                        || adType == AdType.LARGE_1) {

                    starRatingView.setVisibility(
                            View.GONE
                    );

                } else {

                    if (nativeAd.getStarRating() == null) {

                        starRatingView.setVisibility(
                                View.GONE
                        );

                    } else if (
                            starRatingView
                                    instanceof RatingBar
                    ) {

                        starRatingView.setVisibility(
                                View.VISIBLE
                        );

                        ((RatingBar) starRatingView)
                                .setRating(
                                        nativeAd
                                                .getStarRating()
                                                .floatValue()
                                );
                    }
                }
            }

            if (mediaView != null) {

                if (nativeAd.getMediaContent() != null) {

                    mediaView.setVisibility(
                            View.VISIBLE
                    );

                } else {

                    mediaView.setVisibility(
                            View.GONE
                    );
                }
            }

            if (ctaView != null) {

                String cta =
                        nativeAd.getCallToAction();

                if (cta != null
                        && !cta.trim().isEmpty()) {

                    ctaView.setVisibility(
                            View.VISIBLE
                    );

                    if (ctaView instanceof TextView) {

                        ((TextView) ctaView)
                                .setText(cta);
                    }

                    applyButtonColor(
                            ctaView
                    );

                } else {

                    ctaView.setVisibility(
                            View.INVISIBLE
                    );
                }
            }

            adView.registerNativeAd(
                    nativeAd,
                    mediaView
            );

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Failed to bind Next-Gen native ad",
                    e
            );
        }
    }

    /**
     * Applies configured CTA button color.
     */
    private void applyButtonColor(View ctaView) {

        int backgroundColor =
                Color.parseColor("#008000");

        if (buttonColor != null
                && !buttonColor.trim().isEmpty()) {

            try {

                backgroundColor =
                        Color.parseColor(
                                buttonColor.trim()
                        );

            } catch (Exception firstException) {

                try {

                    String color =
                            buttonColor.trim();

                    if (!color.startsWith("#")) {
                        color = "#" + color;
                    }

                    backgroundColor =
                            Color.parseColor(color);

                } catch (Exception ignored) {

                    Log.w(
                            TAG,
                            "Invalid button color: "
                                    + buttonColor
                    );
                }
            }
        }

        Drawable drawable =
                ctaView.getBackground();

        if (drawable != null) {

            drawable =
                    drawable.mutate();

            drawable.setColorFilter(
                    backgroundColor,
                    android.graphics.PorterDuff.Mode.SRC
            );

            ctaView.setBackground(drawable);

        } else {

            ctaView.setBackgroundColor(
                    backgroundColor
            );
        }
    }

    public View getloadingtype(Integer type) {

        if (type == null) {

            return LayoutInflater.from(ctx)
                    .inflate(
                            R.layout.tlib_loading_adaptive_progress,
                            nativeAdContainer,
                            false
                    );
        }

        switch (type) {

            case 3:

                return LayoutInflater.from(ctx)
                        .inflate(
                                R.layout.tlib_loading_adaptive_progress,
                                nativeAdContainer,
                                false
                        );

            case 4:

                return LayoutInflater.from(ctx)
                        .inflate(
                                R.layout.tlib_loading_medium_progress,
                                nativeAdContainer,
                                false
                        );

            case 2:
            case 1:

                return LayoutInflater.from(ctx)
                        .inflate(
                                R.layout.tlib_loading_large_progress,
                                nativeAdContainer,
                                false
                        );

            default:

                return LayoutInflater.from(ctx)
                        .inflate(
                                R.layout.tlib_loading_adaptive_progress,
                                nativeAdContainer,
                                false
                        );
        }
    }

    public void destroy() {

        runOnMainThread(() -> {

            nativeAdContainer.removeAllViews();

            destroyCurrentAd();
        });
    }

    private void destroyCurrentAd() {

        if (currentNativeAd != null) {

            try {
                currentNativeAd.destroy();
            } catch (Exception e) {

                Log.w(
                        TAG,
                        "Error destroying native ad",
                        e
                );
            }

            currentNativeAd = null;
        }
    }

    private int safeInt(Integer value) {

        return value == null ? 0 : value;
    }

    private void runOnMainThread(Runnable action) {

        if (Looper.myLooper()
                == Looper.getMainLooper()) {

            action.run();

        } else {

            mainHandler.post(action);
        }
    }
}

/**
 * Native ad layout/type mapping.
 */
enum AdType {

    BANNER,
    SMALL,
    MEDIUM,
    LARGE,
    LARGE_1,
    ADAPTIVE;

    public static AdType fromInt(Integer type) {

        if (type == null) {
            return SMALL;
        }

        switch (type) {

            case 3:
                return SMALL;

            case 4:
                return MEDIUM;

            case 2:
                return LARGE;

            case 1:
                return LARGE_1;

            default:
                return SMALL;
        }
    }
}