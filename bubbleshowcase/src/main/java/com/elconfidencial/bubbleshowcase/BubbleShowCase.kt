package com.elconfidencial.bubbleshowcase


import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.support.v4.content.ContextCompat
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import java.lang.ref.WeakReference


/**
 * Created by jcampos on 04/09/2018.
 */

class BubbleShowCase(builder: BubbleShowCaseBuilder){
    private val FOREGROUND_LAYOUT_ID = 731

    private val DURATION_SHOW_CASE_ANIMATION = 200 //ms
    private val DURATION_BACKGROUND_ANIMATION = 700 //ms
    private val DURATION_BEATING_ANIMATION = 700 //ms

    private val MAX_WIDTH_MESSAGE_VIEW_TABLET = 420 //dp

    /**
     * Enum class which corresponds to each valid position for the BubbleMessageView arrow
     */
    enum class ArrowPosition {
        TOP, BOTTOM, LEFT, RIGHT
    }

    private val mActivity: WeakReference<Activity> = builder.mActivity!!

    //BubbleMessageView params
    private val mImage: Drawable? = builder.mImage
    private val mTitle: String? = builder.mTitle
    private val mSubtitle: String? = builder.mSubtitle
    private val mCloseAction: Drawable? = builder.mCloseAction
    private val mBackgroundColor: Int? = builder.mBackgroundColor
    private val mTextColor: Int? = builder.mTextColor
    private val mTitleTextSize: Int? = builder.mTitleTextSize
    private val mSubtitleTextSize: Int? = builder.mSubtitleTextSize
    private val mArrowPositionList: MutableList<ArrowPosition> = builder.mArrowPositionList
    private val mTargetView: WeakReference<View>? = builder.mTargetView
    private val mBubbleShowCaseListener: BubbleShowCaseListener?  = builder.mBubbleShowCaseListener

    //Sequence params
    private val mSequenceListener: SequenceShowCaseListener?  = builder.mSequenceShowCaseListener
    private val isFirstOfSequence: Boolean  = builder.mIsFirstOfSequence!!
    private val isLastOfSequence: Boolean  = builder.mIsLastOfSequence!!

    //References
    private var foregroundLayoutWithBlur: RelativeLayout? = null
    private var bubbleMessageViewBuilder: BubbleMessageView.Builder? = null

    fun show(){
        val rootView = getViewRoot(mActivity.get()!!)
        foregroundLayoutWithBlur = getForegroundLayoutWithBlur()
        bubbleMessageViewBuilder = getBubbleMessageViewBuilder()

        if (mTargetView != null && mArrowPositionList.size <= 1) {
            //Wait until the end of the layout animation, to avoid problems with pending scrolls or view movements
            val handler = Handler()
            handler.postDelayed({
                val target = mTargetView.get()!!
                //If the arrow list is empty, the arrow position is set by default depending on the targetView position on the screen
                if(mArrowPositionList.isEmpty()){
                    if(ScreenUtils.isViewLocatedAtHalfTopOfTheScreen(mActivity.get()!!, target)) mArrowPositionList.add(ArrowPosition.TOP) else mArrowPositionList.add(ArrowPosition.BOTTOM)
                    bubbleMessageViewBuilder = getBubbleMessageViewBuilder()
                }

                if (ScreenUtils.isVisibleOnScreen(target)) {
                    addTargetViewAtForegroundLayout(target, foregroundLayoutWithBlur)
                    addBubbleMessageViewDependingOnTargetView(target, bubbleMessageViewBuilder!!, foregroundLayoutWithBlur)
                } else {
                    dismiss()
                }
            }, DURATION_BACKGROUND_ANIMATION.toLong())
        } else {
            addBubbleMessageViewOnScreenCenter(bubbleMessageViewBuilder!!, foregroundLayoutWithBlur)
        }
        if(isFirstOfSequence){
            //Add the foreground layout above the root view
            val animation = AnimationUtils.getFadeInAnimation(0, DURATION_BACKGROUND_ANIMATION)
            rootView.addView(AnimationUtils.setAnimationToView(foregroundLayoutWithBlur!!, animation))
        }
    }

    fun dismiss() {
        mSequenceListener?.let { mSequenceListener.onDismiss() }
        if (foregroundLayoutWithBlur != null && isLastOfSequence) {
            //Remove foreground layout if the BubbleShowCase is the last of the sequence
            val rootView = getViewRoot(mActivity.get()!!)
            rootView.removeView(foregroundLayoutWithBlur)
            foregroundLayoutWithBlur = null
        } else {
            //Remove all the views created over the foreground layout waiting for the next BubbleShowCsse in the sequence
            foregroundLayoutWithBlur!!.removeAllViews()
        }
    }

    private fun getViewRoot(activity: Activity): ViewGroup {
        val androidContent = activity.findViewById<ViewGroup>(android.R.id.content)
        return androidContent.parent.parent as ViewGroup
    }

    private fun getForegroundLayoutWithBlur(): RelativeLayout {
        if(mActivity.get()!!.findViewById<RelativeLayout>(FOREGROUND_LAYOUT_ID) != null)
            return mActivity.get()!!.findViewById(FOREGROUND_LAYOUT_ID)
        val backgroundLayout = RelativeLayout(mActivity.get()!!)
        backgroundLayout.id = FOREGROUND_LAYOUT_ID
        backgroundLayout.layoutParams = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        backgroundLayout.setBackgroundColor(ContextCompat.getColor(mActivity.get()!!, R.color.transparent_grey))
        backgroundLayout.isClickable = true
        return backgroundLayout
    }

    private fun getBubbleMessageViewBuilder(): BubbleMessageView.Builder{
        return BubbleMessageView.Builder()
                .from(mActivity.get()!!)
                .arrowPosition(mArrowPositionList)
                .backgroundColor(mBackgroundColor)
                .textColor(mTextColor)
                .titleTextSize(mTitleTextSize)
                .subtitleTextSize(mSubtitleTextSize)
                .title(mTitle)
                .subtitle(mSubtitle)
                .image(mImage)
                .closeActionImage(mCloseAction)
                .listener(object : OnDismissBubbleMessageViewListener{
                    override fun onDismiss() {
                        dismiss()
                        mBubbleShowCaseListener?.onClose(this@BubbleShowCase)
                    }
                })
    }

    /**
     * This function takes a screenshot of the targetView, creating an ImageView from it. This new ImageView is also set on the layout passed by param
     */
    private fun addTargetViewAtForegroundLayout(targetView: View?, foregroundLayout: RelativeLayout?) {
        if(targetView==null) return

        val targetScreenshot = takeScreenshot(targetView)
        val targetScreenshotView = ImageView(mActivity.get()!!)
        targetScreenshotView.setImageBitmap(targetScreenshot)
        targetScreenshotView.setOnClickListener {
            dismiss()
            mBubbleShowCaseListener?.onTargetClick(this)
        }

        val targetViewParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT)
        targetViewParams.setMargins(getXposition(targetView), getYposition(targetView), 0, 0)
        foregroundLayout?.addView(AnimationUtils.setBouncingAnimation(targetScreenshotView, 0, DURATION_BEATING_ANIMATION), targetViewParams)
    }

    /**
     * This function creates the BubbleMessageView depending the position of the target and the desired arrow position. This new view is also set on the layout passed by param
     */
    private fun addBubbleMessageViewDependingOnTargetView(targetView: View?, bubbleMessageViewBuilder: BubbleMessageView.Builder, foregroundLayout: RelativeLayout?) {
        if(targetView==null) return
        val showCaseParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT)

        when (bubbleMessageViewBuilder.mArrowPosition[0]) {
            ArrowPosition.LEFT -> {
                showCaseParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT)
                if(ScreenUtils.isViewLocatedAtHalfTopOfTheScreen(mActivity.get()!!, targetView)){
                    showCaseParams.setMargins(
                            getXposition(targetView) + targetView.width,
                            getYposition(targetView),
                            if(isTablet()) getScreenWidth(mActivity.get()!!) - (getXposition(targetView) + targetView.width) - getMessageViewWidthOnTablet(getScreenWidth(mActivity.get()!!) - (getXposition(targetView) + targetView.width)) else 0,
                            0)
                    showCaseParams.addRule(RelativeLayout.ALIGN_PARENT_TOP)
                } else{
                    showCaseParams.setMargins(
                            getXposition(targetView) + targetView.width,
                            0,
                            if(isTablet()) getScreenWidth(mActivity.get()!!) - (getXposition(targetView) + targetView.width) - getMessageViewWidthOnTablet(getScreenWidth(mActivity.get()!!) - (getXposition(targetView) + targetView.width)) else 0,
                            getScreenHeight(mActivity.get()!!) - getYposition(targetView) - targetView.height)
                    showCaseParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                }
            }
            ArrowPosition.RIGHT -> {
                showCaseParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
                if(ScreenUtils.isViewLocatedAtHalfTopOfTheScreen(mActivity.get()!!, targetView)){
                    showCaseParams.setMargins(
                            if(isTablet()) getXposition(targetView) - getMessageViewWidthOnTablet(getXposition(targetView)) else 0,
                            getYposition(targetView),
                            getScreenWidth(mActivity.get()!!) - getXposition(targetView),
                            0)
                    showCaseParams.addRule(RelativeLayout.ALIGN_PARENT_TOP)
                } else{
                    showCaseParams.setMargins(
                            if(isTablet()) getXposition(targetView) - getMessageViewWidthOnTablet(getXposition(targetView)) else 0,
                            0,
                            getScreenWidth(mActivity.get()!!) - getXposition(targetView),
                            getScreenHeight(mActivity.get()!!) - getYposition(targetView) - targetView.height)
                    showCaseParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                }
            }
            ArrowPosition.TOP -> {
                showCaseParams.addRule(RelativeLayout.ALIGN_PARENT_TOP)
                if(ScreenUtils.isViewLocatedAtHalfLeftOfTheScreen(mActivity.get()!!, targetView)){
                    showCaseParams.setMargins(
                            if (isTablet()) getXposition(targetView) else 0,
                            getYposition(targetView) + targetView.height,
                            if (isTablet()) getScreenWidth(mActivity.get()!!) - getXposition(targetView) - getMessageViewWidthOnTablet(getScreenWidth(mActivity.get()!!) - getXposition(targetView)) else 0,
                            0)
                } else{
                    showCaseParams.setMargins(
                            if (isTablet()) getXposition(targetView) + targetView.width - getMessageViewWidthOnTablet(getXposition(targetView)) else 0,
                            getYposition(targetView) + targetView.height,
                            if (isTablet()) getScreenWidth(mActivity.get()!!) - getXposition(targetView) - targetView.width else 0,
                            0)
                }
            }
            ArrowPosition.BOTTOM -> {
                showCaseParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                if(ScreenUtils.isViewLocatedAtHalfLeftOfTheScreen(mActivity.get()!!, targetView)){
                    showCaseParams.setMargins(
                            if (isTablet()) getXposition(targetView) else 0,
                            0,
                            if (isTablet()) getScreenWidth(mActivity.get()!!) - getXposition(targetView) - getMessageViewWidthOnTablet(getScreenWidth(mActivity.get()!!) - getXposition(targetView)) else 0,
                            getScreenHeight(mActivity.get()!!) - getYposition(targetView))
                } else {
                    showCaseParams.setMargins(
                            if (isTablet()) getXposition(targetView) + targetView.width - getMessageViewWidthOnTablet(getXposition(targetView)) else 0,
                            0,
                            if (isTablet()) getScreenWidth(mActivity.get()!!) - getXposition(targetView) - targetView.width else 0,
                            getScreenHeight(mActivity.get()!!) - getYposition(targetView))
                }
            }
        }

        val bubbleMessageView = bubbleMessageViewBuilder.targetViewScreenLocation(RectF(
                getXposition(targetView).toFloat(),
                getYposition(targetView).toFloat(),
                getXposition(targetView).toFloat() + targetView.width,
                getYposition(targetView).toFloat() + targetView.height))
                .build()

        bubbleMessageView.id = createViewId()
        val animation = AnimationUtils.getScaleAnimation(0, DURATION_SHOW_CASE_ANIMATION)
        foregroundLayout?.addView(AnimationUtils.setAnimationToView(bubbleMessageView, animation), showCaseParams)
    }

    /**
     * This function creates a BubbleMessageView and it is set on the center of the layout passed by param
     */
    private fun addBubbleMessageViewOnScreenCenter(bubbleMessageViewBuilder: BubbleMessageView.Builder, foregroundLayout: RelativeLayout?) {
        val showCaseParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT)
        showCaseParams.addRule(RelativeLayout. CENTER_VERTICAL)
        val bubbleMessageView: BubbleMessageView = bubbleMessageViewBuilder.build()
        bubbleMessageView.id = createViewId()
        if(isTablet()) showCaseParams.setMargins(
                if (isTablet()) getScreenWidth(mActivity.get()!!)/2 - ScreenUtils.dpToPx(MAX_WIDTH_MESSAGE_VIEW_TABLET)/2 else 0,
                0,
                if (isTablet()) getScreenWidth(mActivity.get()!!)/2 - ScreenUtils.dpToPx(MAX_WIDTH_MESSAGE_VIEW_TABLET)/2 else 0,
                0)
        val animation = AnimationUtils.getScaleAnimation(0, DURATION_SHOW_CASE_ANIMATION)
        foregroundLayout?.addView(AnimationUtils.setAnimationToView(bubbleMessageView, animation), showCaseParams)
    }

    private fun createViewId(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            View.generateViewId()
        } else {
            System.currentTimeMillis().toInt() / 1000
        }
    }

    private fun takeScreenshot(targetView: View): Bitmap? {
        if (targetView.width == 0 || targetView.height == 0) {
            return null
        }

        val rootView = getViewRoot(mActivity.get()!!)
        val currentScreenView = rootView.getChildAt(0)
        currentScreenView.buildDrawingCache()
        val bitmap: Bitmap
        bitmap = Bitmap.createBitmap(currentScreenView.drawingCache, getXposition(targetView), getYposition(targetView), targetView.width, targetView.height)
        currentScreenView.isDrawingCacheEnabled = false
        currentScreenView.destroyDrawingCache()
        return bitmap
    }

    private fun getXposition(targetView: View): Int{
        return ScreenUtils.getAxisXpositionOfViewOnScreen(targetView) - getScreenHorizontalOffset()
    }

    private fun getYposition(targetView: View): Int{
        return ScreenUtils.getAxisYpositionOfViewOnScreen(targetView) - getScreenVerticalOffset()
    }

    private fun getScreenHeight(context: Context): Int{
        return ScreenUtils.getScreenHeight(context) - getScreenVerticalOffset()
    }

    private fun getScreenWidth(context: Context): Int{
        return ScreenUtils.getScreenWidth(context) - getScreenHorizontalOffset()
    }

    private fun getScreenVerticalOffset(): Int{
        return ScreenUtils.getAxisYpositionOfViewOnScreen(foregroundLayoutWithBlur!!)
    }

    private fun getScreenHorizontalOffset(): Int{
        return ScreenUtils.getAxisXpositionOfViewOnScreen(foregroundLayoutWithBlur!!)
    }

    private fun getMessageViewWidthOnTablet(availableSpace: Int): Int{
        return if(availableSpace > ScreenUtils.dpToPx(MAX_WIDTH_MESSAGE_VIEW_TABLET)) ScreenUtils.dpToPx(MAX_WIDTH_MESSAGE_VIEW_TABLET) else availableSpace
    }

    private fun isTablet(): Boolean = mActivity.get()!!.resources.getBoolean(R.bool.isTablet)


}