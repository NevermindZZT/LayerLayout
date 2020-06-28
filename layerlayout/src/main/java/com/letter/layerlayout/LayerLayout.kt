package com.letter.layerlayout

import android.animation.ObjectAnimator
import android.animation.TimeInterpolator
import android.content.Context
import android.graphics.Color
import android.graphics.Point
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import androidx.core.animation.addListener
import androidx.core.view.children
import androidx.core.view.get
import kotlin.math.abs
import kotlin.math.min

private const val TAG = "LayerLayout"
private const val FILTER_VIEW_TAG = "filterView"
private const val MIN_FLING_VELOCITY = 400

/**
 * 滑动距离改变回调
 * @param parent LayerLayout 父布局
 * @param mainView View 主视图
 * @param swipedView View 滑动视图
 * @param process Float 滑动进程(0.0-1.0) 0.0表示视图完全未打开 1.0表示完全打开
 */
typealias SwipedProcessCallback = ((parent: LayerLayout, mainView: View, swipedView: View, process: Float) -> Unit)?

/**
 * LayerLayout
 * Android 层级布局
 *
 * @property viewList MutableList<ViewInfo> 子视图信息列表
 * @property duration Long 视图动画时长
 * @property interpolator LinearInterpolator 视图动画插值器
 * @property enableGesture Boolean 是否启用手势
 * @property enableViewFilter Boolean 是否启用主视图遮罩
 * @property swipedProcessCallback SwipedProcessCallback? 视图滑动回调
 * @property filterView View 遮罩View
 * @property gestureDetector GestureDetector 手势Detector
 * @property mainViewSize Point 主视图大小
 * @constructor 构造器
 *
 * @author Letter(nevermindzzt@gmail.com)
 * @since 1.0.0
 */
class LayerLayout @JvmOverloads
constructor(context: Context, attrs: AttributeSet?=null, defStyleAttr: Int=0, defStyleRes: Int=0)
    : FrameLayout(context, attrs, defStyleAttr, defStyleRes) {

    private val viewList = mutableListOf<ViewInfo>()

    var duration = 200L
    var interpolator = LinearInterpolator()
    var enableGesture = true
    var enableViewFilter = false

    var swipedProcessCallback: SwipedProcessCallback = null

    private val filterView = View(context)

    val gestureDetector = GestureDetector(context, GestureListener())

    private val mainViewSize = Point()

    private var lastMotionX = 0f
    private var lastMotionY = 0f

    private var swiped = false
    private var childSwiped = false
    private var filterViewTouched = false

    private var activeViewGroup: ViewGroup? = null

    init {
        val attrArray = context.obtainStyledAttributes(attrs, R.styleable.LayerLayout)

        duration = attrArray.getInt(R.styleable.LayerLayout_android_duration, 200).toLong()
        enableGesture = attrArray.getBoolean(R.styleable.LayerLayout_enableGesture, true)
        enableViewFilter = attrArray.getBoolean(R.styleable.LayerLayout_enableViewFilter, false)

        attrArray.recycle()

        filterView.setBackgroundColor(Color.parseColor("#80000000"))
        filterView.alpha = 0f
        filterView.tag = FILTER_VIEW_TAG

        filterView.setOnTouchListener { _, motionEvent ->
            filterViewTouched = true
            val resume = this.onTouchEvent(motionEvent)
            filterViewTouched = false
            resume
        }
    }

    /**
     * 触摸事件处理
     * @param event MotionEvent 触摸事件
     * @return Boolean {@code true} 事件被处理 {@code false}事件未被处理
     */
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        return if (enableGesture) {
            gestureDetector.onTouchEvent(event)
        } else {
            super.onTouchEvent(event)
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        return if (onInterceptTouchEvent(event)) {
            onTouchEvent(event)
        } else {
            super.dispatchTouchEvent(event)
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent?): Boolean {
        var resume  = false

        when (event?.action) {
            MotionEvent.ACTION_DOWN -> {
                lastMotionX = event.rawX
                lastMotionY = event.rawY
                resume = false
                childSwiped = false
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.rawX - lastMotionX
                val deltaY = event.rawY - lastMotionY

                val openedViewInfo = getOpenedViewInfo()

                if (openedViewInfo == null) {
                    for (child in children.iterator()) {
                        if ((child is ViewGroup && child.dispatchTouchEvent(event)) || childSwiped) {
                            activeViewGroup = child as ViewGroup
                            Log.d(TAG, "intercept by child")
                            childSwiped = true
                            return true
                        }
                    }
                }

                if (swiped) {
                    resume = true
                } else if (abs(deltaX) > 10 || abs(deltaY) > 10) {
                    val direction = if (abs(deltaX) > abs(deltaY)) {
                        if (deltaX > 0) Direction.LEFT else Direction.RIGHT
                    } else {
                        if (deltaY > 0) Direction.TOP else Direction.BOTTOM
                    }
                    resume = if (openedViewInfo?.direction == Direction.getReverse(direction)) {
                        true
                    } else {
                        getSwipedViewInfo(direction) != null
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                resume = if (childSwiped && activeViewGroup != null) {
                    activeViewGroup?.dispatchTouchEvent(event) !!
                } else {
                    swiped
                }
                childSwiped = false
            }
        }
        Log.d(TAG, "onInterceptTouchEvent: $resume")
        return resume
//        return super.onInterceptTouchEvent(event)
    }

    /**
     * 添加view
     * @see FrameLayout
     */
    override fun addView(child: View?, index: Int, params: ViewGroup.LayoutParams?) {
        super.addView(child, index, params)
        if (child != null) {
            viewList.add(ViewInfo(child, Direction.NONE, Mode.NONE, viewList.size == 0))
            if (viewList.size > 1) {
                initViewState(child, Direction.NONE, viewList.size == 0)
            }
        }
    }

    /**
     * 视图依附于窗口
     * 初始化部分数据
     * @see View
     */
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        mainViewSize.x = 0
    }

    /**
     * 布局
     * 获取main view大小
     * @see View
     */
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (mainViewSize.x == 0) {
            mainViewSize.x = get(0).width
            mainViewSize.y = get(0).height
        }
        if (enableViewFilter) {
            var hasFilter = false
            children.forEach {
                if (it.tag == FILTER_VIEW_TAG) {
                    hasFilter = true
                }
            }
            if (!hasFilter) {
                val layoutParams = ViewGroup.LayoutParams(this.width, this.height)
                filterView.layoutParams = layoutParams
                addView(filterView, 1)
            }
        }
    }

    /**
     * 获取滑动的view信息
     * @param direction Direction 滑动方向
     * @return ViewInfo? view 信息
     */
    fun getSwipedViewInfo(direction: Direction): ViewInfo? {
        for (i in 1 until viewList.size) {
            if (viewList[i].direction == direction) {
                return viewList[i]
            }
        }
        return null
    }

    /**
     * 获取被打开的view 信息
     * @return ViewInfo? view 信息
     */
    fun getOpenedViewInfo(): ViewInfo? {
        for (i in 1 until viewList.size) {
            if (viewList[i].isOpen) {
                return viewList[i]
            }
        }
        return null
    }

    /**
     * 初始化view状态
     * @param view View view
     * @param direction Direction 打开方向
     * @param isOpen Boolean 是否打开
     */
    private fun initViewState(view: View, direction: Direction, isOpen: Boolean) {
        post {
            view.translationX = when (direction) {
                Direction.LEFT  -> if (isOpen) 0f else -view.width.toFloat()
                Direction.RIGHT -> if (isOpen) 0f else view.width.toFloat()
                else -> 0f
            }
            view.translationY = when (direction) {
                Direction.TOP  -> if (isOpen) 0f else -view.height.toFloat()
                Direction.BOTTOM -> if (isOpen) 0f else view.height.toFloat()
                else -> 0f
            }
        }
    }

    /**
     * 获取打开方向
     * @param view View view
     * @return Direction 打开方向
     */
    fun getViewDirection(view: View): Direction {
        viewList.forEach {
            if (it.view == view) {
                return it.direction
            }
        }
        return Direction.LEFT
    }

    /**
     * 设置打开方向
     * @param view View view
     * @param direction Direction 打开方向
     */
    fun setViewDirection(view: View, direction: Direction) {
        viewList.forEach {
            if (it.view == view) {
                it.direction = direction
                initViewState(view, direction, it.isOpen)
            }
        }
    }

    /**
     * 根据view 索引设置打开方向
     * @param index Int 索引
     * @param direction Direction 打开方向
     */
    fun setViewDirection(index: Int, direction: Direction) {
        if (index < viewList.size) {
            setViewDirection(viewList[index].view, direction)
        }
    }

    /**
     * 根据view id设置打开方向
     * @param id Int view id
     * @param direction Direction 打开方向
     */
    fun setViewDirectionById(id: Int, direction: Direction) {
        viewList.forEach {
            if (it.view.id == id) {
                setViewDirection(it.view, direction)
            }
        }
    }

    /**
     * 获取打开模式
     * @param view View view
     * @return Mode 打开模式
     */
    fun getViewMode(view: View): Mode {
        viewList.forEach {
            if (it.view == view) {
                return it.mode
            }
        }
        return Mode.NONE
    }

    /**
     * 设置view打开模式
     * @param view View view
     * @param mode Mode 打开模式
     */
    fun setViewMode(view: View, mode: Mode) {
        viewList.forEach {
            if (it.view == view) {
                it.mode = mode
            }
        }
    }

    /**
     * 根据索引设置view打开模式
     * @param index Int 索引
     * @param mode Mode 打开模式
     */
    fun setViewMode(index: Int, mode: Mode) {
        if (index < viewList.size) {
            viewList[index].mode = mode
        }
    }

    /**
     * 根据view id设置打开模式
     * @param id Int view id
     * @param mode Mode 打开模式
     */
    fun setViewModeById(id: Int, mode: Mode) {
        viewList.forEach {
            if (it.view.id == id) {
                it.mode = mode
            }
        }
    }

    /**
     * 打开或者关闭view
     * @param viewInfo ViewInfo view信息
     * @param isOpen Boolean {@code true}打开 {@code false}关闭
     * @param duration Long 动画时长
     * @param interpolator TimeInterpolator 动画插值器
     */
    fun openOrCloseView(viewInfo: ViewInfo,
                        isOpen: Boolean,
                        duration: Long = this.duration,
                        interpolator: TimeInterpolator = this.interpolator) {
        val translation =
            when (viewInfo.direction) {
                Direction.LEFT, Direction.RIGHT -> {
                    if (isOpen) 0f else viewInfo.view.width.toFloat()
                }
                Direction.TOP, Direction.BOTTOM -> {
                    if (isOpen) 0f else viewInfo.view.height.toFloat()
                }
                else -> 0f
            }
        val viewWrapper = ViewWrapper(viewInfo)
        ObjectAnimator.ofFloat(viewWrapper,
            "translation",
            translation)
            .start {
                this.duration = duration
                this.interpolator = interpolator
                addListener(
                    onEnd = {
                        viewInfo.isOpen = isOpen
                    }
                )
            }
    }

    /**
     * 打开view
     * @param view View view
     * @param duration Long 动画时长
     * @param interpolator TimeInterpolator 动画插值器
     */
    fun openView(view: View,
                 duration: Long = this.duration,
                 interpolator: TimeInterpolator = this.interpolator) {
        viewList.forEach {
            if (it.view == view && !it.isOpen) {
                openOrCloseView(it, true, duration, interpolator)
            }
        }
    }

    /**
     * 根据view 索引打开view
     * @param index Int 索引
     * @param duration Long 动画时长
     * @param interpolator TimeInterpolator 动画插值器
     */
    fun openView(index: Int,
                 duration: Long = this.duration,
                 interpolator: TimeInterpolator = this.interpolator) {
        if (index < viewList.size) {
            openView(viewList[index].view, duration, interpolator)
        }
    }

    /**
     * 根据view id打开view
     * @param id Int view id
     * @param duration Long 动画时长
     * @param interpolator TimeInterpolator 动画插值器
     */
    fun openViewById(id: Int,
                     duration: Long = this.duration,
                     interpolator: TimeInterpolator = this.interpolator) {
        viewList.forEach {
            if (it.view.id == id) {
                openView(it.view, duration, interpolator)
            }
        }
    }

    /**
     * 关闭view
     * @param view View view
     * @param duration Long 动画时长
     * @param interpolator TimeInterpolator 动画插值器
     */
    fun closeView(view: View,
                  duration: Long = this.duration,
                  interpolator: TimeInterpolator = this.interpolator) {
        viewList.forEach {
            if (it.view == view && it.isOpen) {
                openOrCloseView(it, false, duration, interpolator)
            }
        }
    }

    /**
     * 更具view 索引关闭View
     * @param index Int 索引
     * @param duration Long 动画时长
     * @param interpolator TimeInterpolator 动画插值器
     */
    fun closeView(index: Int,
                  duration: Long = this.duration,
                  interpolator: TimeInterpolator = this.interpolator) {
        if (index < viewList.size) {
            closeView(viewList[index].view, duration, interpolator)
        }
    }

    /**
     * 根据view id关闭view
     * @param id Int view id
     * @param duration Long 动画时长
     * @param interpolator TimeInterpolator 动画插值器
     */
    fun closeViewById(id: Int,
                      duration: Long = this.duration,
                      interpolator: TimeInterpolator = this.interpolator) {
        viewList.forEach {
            if (it.view.id == id) {
                closeView(it.view, duration, interpolator)
            }
        }
    }

    /**
     * 打开所有视图
     */
    fun openAll() {
        for (i in 0 until viewList.size) {
            openView(i)
        }
    }

    /**
     * 关闭所有视图
     */
    fun closeAll() {
        for (i in 0 until viewList.size) {
            closeView(i)
        }
    }

    /**
     * 获取view translation
     * @param viewInfo ViewInfo view信息
     * @return Float translation
     */
    private fun getViewTranslation(viewInfo: ViewInfo) =
        when (viewInfo.direction) {
            Direction.LEFT -> -viewInfo.view.translationX
            Direction.RIGHT -> viewInfo.view.translationX
            Direction.TOP -> -viewInfo.view.translationY
            Direction.BOTTOM -> viewInfo.view.translationY
            else -> 0f
        }

    /**
     * 设置 View 位置
     * @param viewInfo ViewInfo view信息
     * @param translation Float translation
     */
    private fun setViewTranslation(viewInfo: ViewInfo, translation: Float) {
        val maxTrans = when (viewInfo.direction) {
            Direction.LEFT, Direction.RIGHT -> viewInfo.view.width
            Direction.TOP, Direction.BOTTOM -> viewInfo.view.height
            else -> 0
        }.toFloat()
        var trans = min(maxTrans, translation)
        if (trans < 0) {
            trans = 0f
        }
        filterView.alpha = (maxTrans - trans) / maxTrans
        filterView.visibility = if (filterView.alpha == 0f) View.GONE else View.VISIBLE
        when (viewInfo.direction) {
            Direction.LEFT -> {
                viewInfo.view.translationX = -trans
                when (viewInfo.mode) {
                    Mode.CENTER -> get(0).translationX = (viewInfo.view.width + viewInfo.view.translationX) / 2
                    Mode.ABSOLUTE -> get(0).translationX = viewInfo.view.width + viewInfo.view.translationX
                    Mode.COLLAPSE -> {
                        get(0).translationX = viewInfo.view.width + viewInfo.view.translationX
                        get(0).layoutParams.width =
                            mainViewSize.x - (viewInfo.view.width + viewInfo.view.translationX).toInt()
                        get(0).requestLayout()
                    }
                    else -> Unit
                }
            }
            Direction.RIGHT ->  {
                viewInfo.view.translationX = trans
                when (viewInfo.mode) {
                    Mode.CENTER -> get(0).translationX = -(viewInfo.view.width - viewInfo.view.translationX) / 2
                    Mode.ABSOLUTE -> get(0).translationX = -(viewInfo.view.width - viewInfo.view.translationX)
                    Mode.COLLAPSE -> {
                        get(0).layoutParams.width =
                            mainViewSize.x - (viewInfo.view.width - viewInfo.view.translationX).toInt()
                        get(0).requestLayout()
                    }
                    else -> Unit
                }
            }
            Direction.TOP ->  {
                viewInfo.view.translationY = -trans
                when (viewInfo.mode) {
                    Mode.CENTER -> get(0).translationY = (viewInfo.view.height + viewInfo.view.translationY) / 2
                    Mode.ABSOLUTE -> get(0).translationY = viewInfo.view.height + viewInfo.view.translationY
                    Mode.COLLAPSE -> {
                        get(0).translationY = viewInfo.view.height + viewInfo.view.translationY
                        get(0).layoutParams.height =
                            mainViewSize.y - (viewInfo.view.height + viewInfo.view.translationY).toInt()
                        get(0).requestLayout()
                    }
                    else -> Unit
                }
            }
            Direction.BOTTOM ->  {
                viewInfo.view.translationY = trans
                when (viewInfo.mode) {
                    Mode.CENTER -> get(0).translationY = -(viewInfo.view.height - viewInfo.view.translationY) / 2
                    Mode.ABSOLUTE -> get(0).translationY = -(viewInfo.view.height - viewInfo.view.translationY)
                    Mode.COLLAPSE -> {
                        get(0).layoutParams.height =
                            mainViewSize.y - (viewInfo.view.height - viewInfo.view.translationY).toInt()
                        get(0).requestLayout()
                    }
                    else -> Unit
                }
            }
            else -> Unit
        }
        swipedProcessCallback?.invoke(this, get(0), viewInfo.view, (maxTrans - trans) / maxTrans)
    }

    /**
     * View打开方向
     * @property value Int 方向值
     * @constructor 构造器
     */
    enum class Direction(private val value: Int) {
        NONE(-1), LEFT(0), RIGHT(1), TOP(2), BOTTOM(3);

        companion object {
            /**
             * 根据值获取Direction
             * @param value Int 值
             * @return Direction 方向
             */
            fun getByValue(value: Int): Direction {
                values().forEach {
                    if (it.value == value) {
                        return it
                    }
                }
                return LEFT
            }

            fun getReverse(direction: Direction) =
                when (direction) {
                    LEFT -> RIGHT
                    RIGHT -> RIGHT
                    TOP -> BOTTOM
                    BOTTOM -> TOP
                    else -> NONE
                }
        }
    }

    /**
     * View 打开模式
     * @property value Int 模式值
     * @constructor 构造器
     */
    enum class Mode(private val value: Int) {
        NONE(0), CENTER(1), ABSOLUTE(2), COLLAPSE(3);

        companion object {
            /**
             * 更具值获取Mode
             * @param value Int 值
             * @return Mode 模式
             */
            fun getByValue(value: Int): Mode {
                values().forEach {
                    if (it.value == value) {
                        return it
                    }
                }
                return NONE
            }
        }
    }

    /**
     * View Info
     * @property view View view
     * @property direction Direction view打开方向
     * @property mode Mode view打开模式
     * @property isOpen Boolean 是否打开
     * @constructor 构造器
     */
    data class ViewInfo(val view: View,
                        var direction: Direction,
                        var mode: Mode,
                        var isOpen: Boolean)


    /**
     * ViewWrapper
     * 用于执行属性动画
     * @property viewInfo ViewInfo viewInfo
     * @constructor 构造器
     */
    inner class ViewWrapper(private val viewInfo: ViewInfo) {

        fun setTranslation(translation: Float) {
            setViewTranslation(viewInfo, translation)
        }

        fun getTranslation() = getViewTranslation(viewInfo)
    }

    /**
     * 手势监听
     * @property view View? view
     * @property isDown Boolean 按下
     * @property swipeDirection Direction 滑动方向
     * @property swipeCount Int 滑动计数
     * @constructor 构造器
     */
    open inner class GestureListener: GestureDetector.OnGestureListener {

        private var isDown = false
        private var swipeDirection = Direction.RIGHT
        private var swipeCount = 0

        override fun onShowPress(p0: MotionEvent?) = Unit

        override fun onSingleTapUp(p0: MotionEvent?) =
            if (filterViewTouched) {
                closeAll()
                true
            } else {
                false
            }

        override fun onDown(p0: MotionEvent?): Boolean {
            Log.d(TAG, "onDown")
            swipeCount = 0
            isDown = false
            return true
        }

        override fun onFling(p0: MotionEvent?, p1: MotionEvent?, p2: Float, p3: Float): Boolean {
            Log.d(TAG, "onFling")
            swiped = false
            val viewInfo = getSwipedViewInfo(swipeDirection)
            if (viewInfo != null) {
                when (swipeDirection) {
                    Direction.LEFT,
                    Direction.RIGHT -> {
                        if (swipeCount < 10 || abs(p2) > MIN_FLING_VELOCITY) {
                            openOrCloseView(viewInfo,
                                viewInfo.direction == if (p2 > 0) Direction.LEFT else Direction.RIGHT)
                        } else {
                            openOrCloseView(viewInfo,
                                abs(viewInfo.view.translationX) < viewInfo.view.width / 2)
                        }
                    }
                    Direction.TOP,
                    Direction.BOTTOM -> {
                        if (swipeCount < 10 || abs(p3) > MIN_FLING_VELOCITY) {
                            openOrCloseView(viewInfo,
                                viewInfo.direction == if (p3 < 0) Direction.BOTTOM else Direction.TOP)
                        } else {
                            openOrCloseView(viewInfo,
                                abs(viewInfo.view.translationY) < viewInfo.view.height / 2)
                        }
                    }
                    else -> Unit
                }
            } else {
                return false
            }
            isDown = false
            return true
        }

        override fun onScroll(p0: MotionEvent?, p1: MotionEvent?, p2: Float, p3: Float) : Boolean {
            Log.d(TAG, "onScroll")
            swipeCount++
            if (!isDown) {
                isDown = true
                val openedViewInfo = getOpenedViewInfo()
                swipeDirection =
                    openedViewInfo?.direction
                        ?: if (abs(p2) >= abs((p3))) {
                            if (p2 < 0) Direction.LEFT else Direction.RIGHT
                        } else {
                            if (p3 < 0) Direction.TOP else Direction.BOTTOM
                        }
            }
            val viewInfo = getSwipedViewInfo(swipeDirection)
            if (viewInfo != null) {
                when (swipeDirection) {
                    Direction.LEFT -> {
                        setViewTranslation(viewInfo, getViewTranslation(viewInfo) + p2)
                    }
                    Direction.RIGHT -> {
                        setViewTranslation(viewInfo, getViewTranslation(viewInfo) - p2)
                    }
                    Direction.TOP -> {
                        setViewTranslation(viewInfo, getViewTranslation(viewInfo) + p3)
                    }
                    Direction.BOTTOM -> {
                        setViewTranslation(viewInfo, getViewTranslation(viewInfo) - p3)
                    }
                    else -> Unit
                }
                swiped = true
                return true
            }
            return false
        }

        override fun onLongPress(p0: MotionEvent?) = Unit
    }
}

/**
 * 属性动画扩展
 * @receiver ObjectAnimator 属性动画
 * @param act [@kotlin.ExtensionFunctionType] Function1<ObjectAnimator, Unit>? 动作
 */
internal fun ObjectAnimator.start(act: (ObjectAnimator.()->Unit)? = null) {
    act?.invoke(this)
    start()
}
