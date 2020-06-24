package com.letter.layerlayout.activity

import android.content.toast
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import com.google.android.material.navigation.NavigationView
import com.letter.layerlayout.LayerLayout
import com.letter.layerlayout.R
import com.letter.layerlayout.databinding.ActivityMainBinding
import com.letter.presenter.ViewPresenter

private const val TAG = "MainActivity"

class MainActivity : AppCompatActivity(), ViewPresenter
    , NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

        setSupportActionBar(binding.toolbar)
        initBinding()
        binding.layerLayout.setViewDirectionById(R.id.nav_view, LayerLayout.Direction.LEFT)
        binding.layerLayout.setViewModeById(R.id.nav_view, LayerLayout.Mode.NONE)
    }

    private fun initBinding() {
        binding.let {
            it.lifecycleOwner = this@MainActivity
            it.presenter = this
        }
        binding.bottomLayout.let {
            it.lifecycleOwner = this@MainActivity
            it.presenter = this
        }
        binding.navView.setNavigationItemSelectedListener(this)
        binding.layerLayout.swipedProcessCallback = {
            parent, mainView, swipedView, process ->
            when (swipedView.id) {
                R.id.nav_view -> {
                    swipedView.scaleX = process / 10 + 0.9f
                    swipedView.scaleY = process / 10 + 0.9f
                }
            }
        }
//        binding.cardItem.setOnTouchListener { _, motionEvent ->
//            if (binding.cardLayout.onTouchEvent(motionEvent)) true else binding.cardItem.onTouchEvent(motionEvent)
//        }
    }

    override fun onClick(view: View?) {
        when (view?.id) {
            R.id.open_left_button -> binding.layerLayout.openViewById(R.id.bottom_layout)
            R.id.close_left_button -> {
                binding.layerLayout.closeViewById(R.id.bottom_layout)
                Log.d(TAG, "close left layout")
            }
            R.id.card_item -> toast("card item clicked")
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.close -> binding.layerLayout.closeViewById(R.id.nav_view)
        }
        return true
    }


}