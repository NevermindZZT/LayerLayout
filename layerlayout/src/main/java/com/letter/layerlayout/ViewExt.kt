package com.letter.layerlayout

import android.view.View
import androidx.databinding.BindingAdapter

/**
 * View 打开方向扩展
 */
var View.direction: LayerLayout.Direction
    get() =
        if (parent is LayerLayout) {
            (parent as LayerLayout).getViewDirection(this)
        } else {
            LayerLayout.Direction.NONE
        }
    set(value) {
        if (parent is LayerLayout) {
            (parent as LayerLayout).setViewDirection(this, value)
        }
    }

/**
 * View 打开模式扩展
 */
var View.mode: LayerLayout.Mode
    get() =
        if (parent is LayerLayout) {
            (parent as LayerLayout).getViewMode(this)
        } else {
            LayerLayout.Mode.NONE
        }
    set(value) {
        if (parent is LayerLayout) {
            (parent as LayerLayout).setViewMode(this, value)
        }
    }

/**
 * 判断View是否被打开
 * @receiver View view
 * @return Boolean {@code true}打开 {@code false}关闭
 */
fun View.isOpened() =
    if (parent is LayerLayout) {
        (parent as LayerLayout).isViewOpened(this)
    } else {
        false
    }

/**
 * 设置view打开方向
 * @param view View view
 * @param direction Int 打开方向
 */
@BindingAdapter("direction")
fun setViewDirection(view: View, direction: Int) {
    view.direction = LayerLayout.Direction.getByValue(direction)
}

/**
 * 设置view打开方向
 * @param view View view
 * @param direction Int 打开方向
 */
@BindingAdapter("direction")
fun setViewDirection(view: View, direction: LayerLayout.Direction) {
    view.direction = direction
}

/**
 * 设置View 打开模式
 * @param view View view
 * @param mode Int 打开模式
 */
@BindingAdapter("mode")
fun setViewMode(view: View, mode: Int) {
    view.mode = LayerLayout.Mode.getByValue(mode)
}

/**
 * 设置View 打开模式
 * @param view View view
 * @param mode Int 打开模式
 */
@BindingAdapter("mode")
fun setViewMode(view: View, mode: LayerLayout.Mode) {
    view.mode = mode
}