package com.example.bryanpaulyabut_labex08_comp3074;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String PREFS = "places_prefs";
    private static final String KEY = "favorites"; // JSON array of objects {lat:..., lng:...}

    private ListView listView;
    private ArrayAdapter<String> adapter;
    private List<String> items = new ArrayList<>();
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        listView = findViewById(R.id.places_list);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, items);
        listView.setAdapter(adapter);

        FloatingActionButton fab = findViewById(R.id.fab_add);
        fab.setOnClickListener(v -> {
            // Open map activity to add a new favorite
            Intent i = new Intent(MainActivity.this, MapsActivity.class);
            startActivity(i);
        });

        loadFavorites();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadFavorites();
    }

    private void loadFavorites(){
        items.clear();
        String json = prefs.getString(KEY, "[]");
        try{
            JSONArray arr = new JSONArray(json);
            for(int i=0;i<arr.length();i++){
                org.json.JSONObject obj = arr.getJSONObject(i);
                double lat = obj.optDouble("lat");
                double lng = obj.optDouble("lng");
                String title = obj.optString("title", "Favorite");
                items.add(title + " (" + lat + ", " + lng + ")");
            }
        }catch(JSONException e){
            e.printStackTrace();
        }
        adapter.notifyDataSetChanged();
    }
}

