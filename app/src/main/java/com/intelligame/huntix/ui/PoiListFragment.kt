package com.intelligame.huntix.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.intelligame.huntix.R
import com.intelligame.huntix.manager.PoiMapBridge
import com.intelligame.huntix.legacy.Util.HuntixPoiBridge

/**
 * PoiListFragment — UI Kotlin alternativa a MapActivity per sfogliare i negozi POI.
 *
 * Legge i PO del bridge (popolati da PoiMapBridge feed) e permette
 * di aprire la pagina JSON o web del negozio, come MapActivity.
 * Apribile da BaseNavActivity ("Esplora" alternativo) o da un tab.
 */
class PoiListFragment : Fragment(R.layout.fragment_poi_list) {

    private var adapter: PoiAdapter? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_poi_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val recycler = view.findViewById<RecyclerView>(R.id.poiRecycler)
        val empty = view.findViewById<TextView>(R.id.poiEmpty)
        val refresh = view.findViewById<ImageButton>(R.id.poiRefresh)

        adapter = PoiAdapter { poi -> openPoi(poi) }
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        refresh.setOnClickListener { refresh() }

        render()
    }

    private fun render() {
        val pois = HuntixPoiBridge.getPois()
        adapter?.submit(pois)
        val empty = view?.findViewById<TextView>(R.id.poiEmpty)
        empty?.visibility = if (pois.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun refresh() {
        PoiMapBridge.feed(requireContext())
        render()
    }

    private fun openPoi(poi: com.intelligame.huntix.legacy.Model.HuntixPoi) {
        if (poi.hasJsonPage()) {
            startActivity(Intent(requireContext(), POICustomPageActivity::class.java).apply {
                putExtra("json_url", poi.url)
                putExtra("poi_name", poi.name)
                putExtra("poi_building_type", poi.buildingType)
            })
        } else if (poi.hasWebPage()) {
            startActivity(Intent(requireContext(), POIWebViewActivity::class.java).apply {
                putExtra("url", poi.url)
                putExtra("title", poi.name)
            })
        }
    }

    private class PoiAdapter(private val onClick: (com.intelligame.huntix.legacy.Model.HuntixPoi) -> Unit) :
        RecyclerView.Adapter<PoiViewHolder>() {
        private var items: List<com.intelligame.huntix.legacy.Model.HuntixPoi> = emptyList()

        fun submit(list: List<com.intelligame.huntix.legacy.Model.HuntixPoi>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PoiViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_poi, parent, false)
            return PoiViewHolder(v)
        }
        override fun onBindViewHolder(holder: PoiViewHolder, position: Int) { holder.bind(items[position], onClick) }
        override fun getItemCount(): Int = items.size
    }

    private class PoiViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val label: TextView = view.findViewById(R.id.poiLabel)
        private val type: TextView = view.findViewById(R.id.poiType)
        fun bind(poi: com.intelligame.huntix.legacy.Model.HuntixPoi, onClick: (com.intelligame.huntix.legacy.Model.HuntixPoi) -> Unit) {
            label.text = poi.name
            type.text = poi.category()
            itemView.setOnClickListener { onClick(poi) }
        }
    }
}
