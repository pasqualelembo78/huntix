package com.intelligame.huntix.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.intelligame.huntix.R
import java.io.InputStreamReader

class CitySearchActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var searchEditText: EditText
    private lateinit var adapter: CityAdapter
    private var allCities = mutableListOf<City>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_city_search)

        recyclerView = findViewById(R.id.citiesRecyclerView)
        searchEditText = findViewById(R.id.searchEditText)

        loadCities()

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = CityAdapter(allCities) { city ->
            val intent = Intent(this, CityActivity::class.java).apply {
                putExtra("TARGET_LAT", city.lat)
                putExtra("TARGET_LON", city.lon)
                putExtra("TARGET_NAME", city.name)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            finish()
        }
        recyclerView.adapter = adapter

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterCities(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        findViewById<Button>(R.id.closeBtn).setOnClickListener { finish() }
    }

    private fun loadCities() {
        try {
            val input = InputStreamReader(assets.open("italian_cities.json"))
            val data = Gson().fromJson(input, CitiesData::class.java)
            allCities = data.cities.sortedByDescending { it.population }.toMutableList()
            adapter.updateCities(allCities)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun filterCities(query: String) {
        val filtered = if (query.isBlank()) {
            allCities
        } else {
            allCities.filter { it.name.lowercase().contains(query.lowercase()) }
        }
        adapter.updateCities(filtered)
    }

    data class CitiesData(val cities: List<City>)
    data class City(
        val name: String,
        val lat: Double,
        val lon: Double,
        val population: Int,
        val region: String,
        val cap: String
    )

    private class CityAdapter(
        private var cities: List<City>,
        private val onClick: (City) -> Unit
    ) : RecyclerView.Adapter<CityAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val nameText: TextView = view.findViewById(R.id.cityNameText)
            val regionText: TextView = view.findViewById(R.id.cityRegionText)
            val popText: TextView = view.findViewById(R.id.cityPopText)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_city, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val city = cities[position]
            holder.nameText.text = city.name
            holder.regionText.text = "${city.region} • CAP ${city.cap}"
            holder.popText.text = "Pop. ${formatPopulation(city.population)}"
            holder.itemView.setOnClickListener { onClick(city) }
        }

        override fun getItemCount() = cities.size

        fun updateCities(newCities: List<City>) {
            cities = newCities
            notifyDataSetChanged()
        }

        private fun formatPopulation(pop: Int): String {
            return if (pop >= 1000000) String.format("%.1fM", pop / 1_000_000.0)
            else if (pop >= 1000) String.format("%.0fK", pop / 1000.0)
            else pop.toString()
        }
    }
}