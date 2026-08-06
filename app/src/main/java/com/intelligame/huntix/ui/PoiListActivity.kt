package com.intelligame.huntix.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.intelligame.huntix.R

/**
 * Semplice Activity container per PoiListFragment: UI Kotlin alternativa
 * alla mappa Java (MapActivity), navigabile da BaseNavActivity "Altro".
 */
class PoiListActivity : AppCompatActivity(R.layout.activity_poi_list) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.poiListContainer, PoiListFragment())
                .commitNow()
        }
    }
}
