package com.example.bryanpaulyabut_labex08_comp3074;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;
import android.os.Looper;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.example.bryanpaulyabut_labex08_comp3074.databinding.ActivityMapsBinding;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private ActivityMapsBinding binding;
    private FusedLocationProviderClient mClient;
    private LocationRequest request;
    private LocationCallback callback;

    private Marker homeMarker;

    private static final int REQUEST_CODE = 1;
    private static final int UPDATE_INTERVAL = 5000;
    private static final int FASTEST_INTERVAL = 3000;

    List<Marker> markers = new ArrayList<>();
    private static final int POLYGON_SIDES = 3;
    private Polygon shape;

    private SharedPreferences prefs;
    private static final String PREFS = "places_prefs";
    private static final String KEY = "favorites";

    private final ExecutorService geocodeExecutor = Executors.newSingleThreadExecutor();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mClient = LocationServices.getFusedLocationProviderClient(this);

        binding = ActivityMapsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        mMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);

        if (!isGrantedLocationPermission()) {
            requestLocationPermission();

        } else {
            updateLocation();
        }

        initMarkers();
        loadExistingMarkers();
    }

    private void initMarkers() {
        mMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
            @Override
            public void onMapLongClick(@NonNull LatLng latLng) {
                // Reverse geocode on background thread, then show an edit dialog and save
                saveFavoriteAsync(latLng);
            }
        });
    }

    // Reverse geocode asynchronously, prompt user to edit name, then save
    private void saveFavoriteAsync(LatLng latLng) {
        geocodeExecutor.execute(() -> {
            String title = null;
            try {
                Geocoder geocoder = new Geocoder(MapsActivity.this, Locale.getDefault());
                List<Address> addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1);
                if (addresses != null && !addresses.isEmpty()) {
                    Address addr = addresses.get(0);
                    // Prefer thoroughfare + subThoroughfare, fallback to address line
                    String street = addr.getThoroughfare();
                    String number = addr.getSubThoroughfare();
                    if (street != null) {
                        title = (number != null ? number + " " : "") + street;
                    } else {
                        title = addr.getAddressLine(0);
                    }
                }
            } catch (IOException e) {
                // geocoder failed (emulator or network); we'll fallback to coords
                e.printStackTrace();
            }

            if (title == null || title.isEmpty()) {
                title = String.format(Locale.US, "Favorite (%.6f, %.6f)", latLng.latitude, latLng.longitude);
            }

            String suggestedTitle = title;
            uiHandler.post(() -> {
                // show dialog to allow user to edit the title before saving
                final EditText input = new EditText(MapsActivity.this);
                input.setInputType(InputType.TYPE_CLASS_TEXT);
                input.setText(suggestedTitle);
                new AlertDialog.Builder(MapsActivity.this)
                        .setTitle("Save place")
                        .setMessage("Edit the place name:")
                        .setView(input)
                        .setPositiveButton("Save", (dialog, which) -> {
                            String userTitle = input.getText().toString().trim();
                            if (userTitle.isEmpty()) {
                                userTitle = suggestedTitle;
                            }
                            saveFavorite(latLng, userTitle);
                            setMarker(latLng, userTitle);
                            Toast.makeText(MapsActivity.this, "Saved: " + userTitle, Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                        .show();
            });
        });
    }

    private void saveFavorite(LatLng latLng, String title) {
        String json = prefs.getString(KEY, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            JSONObject obj = new JSONObject();
            obj.put("lat", latLng.latitude);
            obj.put("lng", latLng.longitude);
            obj.put("title", title);
            arr.put(obj);
            prefs.edit().putString(KEY, arr.toString()).apply();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void loadExistingMarkers(){
        String json = prefs.getString(KEY, "[]");
        try{
            JSONArray arr = new JSONArray(json);
            for(int i=0;i<arr.length();i++){
                JSONObject obj = arr.getJSONObject(i);
                double lat = obj.optDouble("lat");
                double lng = obj.optDouble("lng");
                String title = obj.optString("title", "Favorite");
                setMarker(new LatLng(lat, lng), title);
            }
        }catch(JSONException e){
            e.printStackTrace();
        }
    }

    // updated to accept a title for the marker
    private void setMarker(LatLng latLng, String title) {
        MarkerOptions options = new MarkerOptions()
                .position(latLng)
                .title(title != null ? title : "Desired Location")
                .draggable(true)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                .snippet("place to visit");
        Marker favMarker = mMap.addMarker(options);

        if (markers.size() == POLYGON_SIDES){
            for (Marker marker : markers){
                marker.remove();
            }
            if (shape != null) shape.remove();
            markers.clear();
        }
        markers.add(favMarker);
        if (markers.size() == POLYGON_SIDES){
            drawShape();
        }
    }

    // keep backward compatibility if other code calls setMarker without title
    private void setMarker(LatLng latLng) {
        setMarker(latLng, "Desired Location");
    }

    private void drawShape() {
        PolygonOptions options = new PolygonOptions()
                .fillColor(0x33ff2200)
                .strokeColor(Color.GREEN)
                .strokeWidth(7);
        for (Marker marker : markers) {
            options.add(marker.getPosition());
        }
        shape = mMap.addPolygon(options);
    }

    private void drawLine(Marker favMarker) {
        PolylineOptions options = new PolylineOptions()
                .color(Color.GREEN)
                .width(10)
                .add(homeMarker.getPosition(), favMarker.getPosition());
        mMap.addPolyline(options);
    }

    private boolean isGrantedLocationPermission() {
        return ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(
                this,
                new String[] {Manifest.permission.ACCESS_FINE_LOCATION},
                REQUEST_CODE
        );
    }

    private void updateLocation() {
        request = new LocationRequest
                .Builder(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        UPDATE_INTERVAL
                )
                .setMinUpdateIntervalMillis(FASTEST_INTERVAL)
                .build();

        callback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                Location location = locationResult.getLastLocation();
                LatLng userLatLng = null;
                if (location != null){
                    userLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                }
                if (userLatLng != null){
                    if (homeMarker != null) homeMarker.remove();
                    homeMarker = mMap.addMarker(
                            new MarkerOptions()
                                    .position(userLatLng)
                                    .title("You are here!")
                    );
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 15) );
                }
            }
        };

        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        mClient.requestLocationUpdates(request, callback, null);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CODE) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                new AlertDialog.Builder(this)
                        .setMessage("The permission is needed for this application")
                        .setPositiveButton("OK", (dialog, which) -> {
                            requestLocationPermission();
                        }).show();
            } else {
                updateLocation();
            }
        }
    }
}
