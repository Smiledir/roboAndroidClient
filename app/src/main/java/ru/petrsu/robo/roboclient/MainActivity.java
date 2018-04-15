package ru.petrsu.robo.roboclient;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Service;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.provider.Settings;
import android.provider.Settings.Secure;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;
import java.util.ArrayList;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class MainActivity extends AppCompatActivity implements LocationListener {

    private final static int ALL_PERMISSIONS_RESULT = 101;

    ArrayList<String> permissions = new ArrayList<>();
    ArrayList<String> permissionsToRequest;
    ArrayList<String> permissionsRejected = new ArrayList<>();

    LocationManager locationManager;
    Location loc;
    boolean isGPS = false;
    boolean isNetwork = false;
    boolean canGetLocation = true;

    private static final long MIN_DISTANCE_CHANGE_FOR_UPDATES = 10;
    private static final long MIN_TIME_BW_UPDATES = 1000 * 60 * 1;

    private EditText mainEditText;
    private Button searchMainButton;
    private Button cancelButton;
    private Button readyButton;
    private TextView waitTextView;
    private TextView mainTextView;
    Socket socket;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mainEditText = findViewById(R.id.editTextMain);
        searchMainButton = findViewById(R.id.buttonSearchMain);
        readyButton = findViewById(R.id.buttonReady);
        cancelButton = findViewById(R.id.buttonCancel);
        waitTextView = findViewById(R.id.textViewWait);
        mainTextView = findViewById(R.id.textViewMain);

        registerLocation();

        registerSockets();
    }

    private void registerSockets(){

        try {
            socket = IO.socket("http://192.168.1.2:4000");
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {

            @Override
            public void call(Object... args) {
                try {
                    Log.d("TAG", "Connect");
                    JSONObject obj = new JSONObject();

                    obj.put("deviceId", Secure.getString(getApplicationContext().getContentResolver(), Secure.ANDROID_ID));

                    socket.emit("firstData", obj);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

        }).on("data", new Emitter.Listener() {

            @Override
            public void call(Object... args) {

                try {
                    String json = (String)args[0];
                    JSONObject obj = new JSONObject(json);
                    Log.d("TAG", "data: " + obj.toString());
                    dataFromServer(obj);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

        }).on("firstData", new Emitter.Listener() {

            @Override
            public void call(Object... args) {
                try {
                    String json = (String)args[0];
                    JSONObject obj = new JSONObject(json);

                    Log.d("TAG", "firstData: " + obj.toString() + " | " + obj.get("status"));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

        }).on("onPlace", new Emitter.Listener() {

            @Override
            public void call(Object... args) {
                try {
                    String json = (String)args[0];
                    JSONObject obj = new JSONObject(json);

                    Log.d("TAG", "onPlace: " + obj.toString() + " | " + obj.get("status"));
                    onPlace();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

        }).on("wayEnd", new Emitter.Listener() {

            @Override
            public void call(Object... args) {
                try {
                    String json = (String)args[0];
                    JSONObject obj = new JSONObject(json);

                    Log.d("TAG", "wayEnd: " + obj.toString());
                    wayEnd();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

        }).on("cancel", new Emitter.Listener() {

            @Override
            public void call(Object... args) {
                try {
                    String json = (String)args[0];
                    JSONObject obj = new JSONObject(json);

                    Log.d("TAG", "cancel: " + obj.toString());
                    cancel(obj.get("error").toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

        }).on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {

            @Override
            public void call(Object... args) {
                Log.d("TAG", "DISCONNECT");
            }

        }).on(Socket.EVENT_CONNECT_ERROR, new Emitter.Listener() {

            @Override
            public void call(Object... args) {
                Log.d("TAG", "Connect ERROR" );
            }

        });
        socket.connect();
    }

    private void onPlace(){
        // Тут нужно что-то вроде: тлежка прибыла
        // Можно попап запустить
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {

                hideUI();
                mainTextView.setVisibility(View.VISIBLE);
                readyButton.setVisibility(View.VISIBLE);

                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("Сообщение!")
                        .setMessage("Тележка прибыла!")
                        .setCancelable(false)
                        .setNegativeButton("ОК",
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        dialog.cancel();
                                    }
                                });
                AlertDialog alert = builder.create();
                alert.show();
            }
        });
    }

    private void cancel(final String message){
        // Тут нужно что-то вроде: что-то пошло не так
        // Можно попап запустить

        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {

                hideUI();
                mainEditText.setVisibility(View.VISIBLE);
                searchMainButton.setVisibility(View.VISIBLE);

                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("Сообщение!")
                        .setMessage("Что-то пошло не так!\n" + message)
                        .setCancelable(false)
                        .setNegativeButton("ОК",
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        dialog.cancel();
                                    }
                                });
                AlertDialog alert = builder.create();
                alert.show();
            }
        });
    }

    private void  wayEnd(){
        // Тут нужно что-то вроде: вы на месте
        // Можно попап запустить
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {

                hideUI();
                mainEditText.setVisibility(View.VISIBLE);
                searchMainButton.setVisibility(View.VISIBLE);
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("Сообщение!")
                        .setMessage("Вы на месте!")
                        .setCancelable(false)
                        .setNegativeButton("ОК",
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        dialog.cancel();
                                    }
                                });
                AlertDialog alert = builder.create();
                alert.show();
            }
        });
    }

    private void hideUI(){
        mainEditText.setVisibility(View.GONE);
        searchMainButton.setVisibility(View.GONE);
        readyButton.setVisibility(View.GONE);
        cancelButton.setVisibility(View.GONE);
        waitTextView.setVisibility(View.GONE);
        mainTextView.setVisibility(View.GONE);
    }

    private void dataFromServer(final JSONObject obj) throws JSONException {

        final String track = obj.getString("trackName");
        String text = getResources().getString(R.string.main_label) + " " + track;
        mainTextView.setText(text);

        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {

                hideUI();
                mainTextView.setVisibility(View.VISIBLE);

                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("Сообщение!")
                        .setMessage("Ожидайте тележку: " + track)
                        .setCancelable(false)
                        .setNegativeButton("ОК",
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        dialog.cancel();
                                    }
                                });
                AlertDialog alert = builder.create();
                alert.show();
            }
        });


    }

    public void buttonReadyClick(View view) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("deviceId", Secure.getString(getApplicationContext().getContentResolver(), Secure.ANDROID_ID));

            socket.emit("startWay", obj);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        hideUI();
        mainTextView.setVisibility(View.VISIBLE);
        cancelButton.setVisibility(View.VISIBLE);
    }

    public void buttonCancelClick(View view) {
        hideUI();
        mainEditText.setVisibility(View.VISIBLE);
        searchMainButton.setVisibility(View.VISIBLE);

        try {
            JSONObject obj = new JSONObject();
            obj.put("deviceId", Secure.getString(getApplicationContext().getContentResolver(), Secure.ANDROID_ID));

            socket.emit("cancel", obj);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void readyButtonClick() throws JSONException {
        JSONObject obj = new JSONObject();

        obj.put("deviceId", Secure.getString(getApplicationContext().getContentResolver(), Secure.ANDROID_ID));

        socket.emit("deviceReady", obj);
    }

    public void buttonSendClick(View v){
        Log.d("TAG", "SendClick");

        EditText editText = (EditText) findViewById(R.id.editTextMain);
        String text = editText.getText().toString();
        try {
            JSONObject obj = new JSONObject();
            obj.put("deviceId", Secure.getString(getApplicationContext().getContentResolver(), Secure.ANDROID_ID));
            obj.put("searchText", text);

            if(loc != null) {
                JSONObject coordinates = new JSONObject();
                coordinates.put("lat", loc.getLatitude());
                coordinates.put("lon", loc.getLongitude());
                obj.put("coordinates", coordinates);
            }

            socket.emit("data", obj);
            hideUI();
            waitTextView.setVisibility(View.VISIBLE);
        }catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void registerLocation(){
        locationManager = (LocationManager) getSystemService(Service.LOCATION_SERVICE);
        isGPS = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        isNetwork = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        permissionsToRequest = findUnAskedPermissions(permissions);

        if (!isGPS && !isNetwork) {
            Log.d("TAG", "Connection off");
            showSettingsAlert();
            getLastLocation();
        } else {
            Log.d("TAG", "Connection on");
            // check permissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (permissionsToRequest.size() > 0) {
                    requestPermissions(permissionsToRequest.toArray(new String[permissionsToRequest.size()]),
                            ALL_PERMISSIONS_RESULT);
                    Log.d("TAG", "Permission requests");
                    canGetLocation = false;
                }
            }

            // get location
            getLocation();
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        getLocation();
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {}

    @Override
    public void onProviderEnabled(String s) {
        getLocation();
    }

    @Override
    public void onProviderDisabled(String s) {
        if (locationManager != null) {
            locationManager.removeUpdates(this);
        }
    }

    private Location getLocation() {
        try {
            if (canGetLocation) {
                Log.d("TAG", "Can get location");
                if (isGPS) {
                    // from GPS
                    Log.d("TAG", "GPS on");
                    locationManager.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER,
                            MIN_TIME_BW_UPDATES,
                            MIN_DISTANCE_CHANGE_FOR_UPDATES, this);

                    if (locationManager != null) {
                        loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                        if (loc != null)
                            return loc;
                    }
                } else if (isNetwork) {
                    // from Network Provider
                    Log.d("TAG", "NETWORK_PROVIDER on");
                    locationManager.requestLocationUpdates(
                            LocationManager.NETWORK_PROVIDER,
                            MIN_TIME_BW_UPDATES,
                            MIN_DISTANCE_CHANGE_FOR_UPDATES, this);

                    if (locationManager != null) {
                        loc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                        if (loc != null)
                            return loc;
                    }
                } else {
                    loc.setLatitude(0);
                    loc.setLongitude(0);
                    return loc;
                }
            } else {
                Log.d("TAG", "Can't get location");
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }

        return null;
    }

    private void getLastLocation() {
        try {
            Criteria criteria = new Criteria();
            String provider = locationManager.getBestProvider(criteria, false);
            Location location = locationManager.getLastKnownLocation(provider);
            Log.d("TAG", provider);
            Log.d("TAG", location == null ? "NO LastLocation" : location.toString());
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    private ArrayList findUnAskedPermissions(ArrayList<String> wanted) {
        ArrayList result = new ArrayList();

        for (String perm : wanted) {
            if (!hasPermission(perm)) {
                result.add(perm);
            }
        }

        return result;
    }

    private boolean hasPermission(String permission) {
        if (canAskPermission()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED);
            }
        }
        return true;
    }

    private boolean canAskPermission() {
        return (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1);
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case ALL_PERMISSIONS_RESULT:
                Log.d("TAG", "onRequestPermissionsResult");
                for (String perms : permissionsToRequest) {
                    Log.d("REMISSION1", perms);
                    if (!hasPermission(perms)) {
                        Log.d("REMISSION", perms);
                        permissionsRejected.add(perms);
                    }
                }

                if (permissionsRejected.size() > 0) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (shouldShowRequestPermissionRationale(permissionsRejected.get(0))) {
                            showMessageOKCancel("These permissions are mandatory for the application. Please allow access.",
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                                requestPermissions(permissionsRejected.toArray(
                                                        new String[permissionsRejected.size()]), ALL_PERMISSIONS_RESULT);
                                            }
                                        }
                                    });
                            return;
                        }
                    }
                } else {
                    Log.d("TAG", "No rejected permissions.");
                    canGetLocation = true;
                    getLocation();
                }
                break;
        }
    }

    public void showSettingsAlert() {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
        alertDialog.setTitle("GPS is not Enabled!");
        alertDialog.setMessage("Do you want to turn on GPS?");
        alertDialog.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                startActivity(intent);
            }
        });

        alertDialog.setNegativeButton("No", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        alertDialog.show();
    }

    private void showMessageOKCancel(String message, DialogInterface.OnClickListener okListener) {
        new AlertDialog.Builder(MainActivity.this)
                .setMessage(message)
                .setPositiveButton("OK", okListener)
                .setNegativeButton("Cancel", null)
                .create()
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (locationManager != null) {
            locationManager.removeUpdates(this);
        }
    }
}
