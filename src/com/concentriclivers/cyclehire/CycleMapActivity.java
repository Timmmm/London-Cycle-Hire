package com.concentriclivers.cyclehire;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Paint;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.*;
import android.widget.Toast;
import org.mapsforge.android.maps.*;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.Timer;
import java.util.TimerTask;

public class CycleMapActivity extends MapActivity
{
	private static final String TAG = "CycleMapActivity";

	private DockOverlay dockOverlay;
	MapView mapView;
	private LocationManager locationManager;
	private Paint locationOverlayFill;
	private Paint locationOverlayOutline;
	private MapController mapController;

	private Location currentLocation; // null if unknown.
	private OverlayCircle locationCircle;

	private ArrayCircleOverlay locationOverlay;

	private static final int TWO_MINUTES = 1000 * 60 * 2;

/*
	@Override
	public Object onRetainNonConfigurationInstance()
	{
		return currentLocation;
	}*/

	boolean updateInstantly = true;

	// Called when the activity is first created.
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		// Request throbber. Must be done before setContentView().
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

		// Check SD card.
		if (!sdOk())
		{
			Log.d("CycleMapActivity", "SD card not mounted.");
			showToast("Error accessing SD card.");
			finish();
			return;
		}

		String storageDir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Android/data/com.concentriclivers.cyclehire/files";
		String filename = storageDir + "/london.map";
		// Write out map file if necessary.
		if (!writeMap(storageDir, "london.map"))
		{
			Log.d("CycleMapActivity", "Couldn't write map.");
			showToast("Error writing map file to SD card.");
			finish();
			return;
		}

		// Create the map view.
        mapView = new MapView(this);
        mapView.setClickable(true);
        mapView.setBuiltInZoomControls(true);
        mapView.setMapFile(filename);
		// Docks overlay.
		dockOverlay = new DockOverlay(this);
		mapView.getOverlays().add(dockOverlay);
		// Location overlay.
		locationOverlayFill = new Paint(Paint.ANTI_ALIAS_FLAG);
		locationOverlayFill.setStyle(Paint.Style.FILL);
		locationOverlayFill.setColor(Color.RED);
		locationOverlayFill.setAlpha(64);

		locationOverlayOutline = new Paint(Paint.ANTI_ALIAS_FLAG);
		locationOverlayOutline.setStyle(Paint.Style.STROKE);
		locationOverlayOutline.setColor(Color.RED);
		locationOverlayOutline.setAlpha(200);
		locationOverlayOutline.setStrokeWidth(5);
		locationOverlay = new ArrayCircleOverlay(locationOverlayFill, locationOverlayOutline, this);
		locationCircle = new OverlayCircle();
		locationOverlay.addCircle(locationCircle);
		mapView.getOverlays().add(locationOverlay);

		// Set up window.
        setContentView(mapView);
		setTitle("Available Bikes");

		// Initialise service references.
		mapController = mapView.getController();
		locationManager = (LocationManager)getSystemService(LOCATION_SERVICE);


		// Initial population of the dockset.
		Object data = getLastNonConfigurationInstance();

		if (data == null)
		{
			// The activity is starting for the first time,
			// Restore dock locations only.
			restoreDockLocations();
			updateInstantly = true; // The update will happen in onResume();

			// Got to last location. But we're not sure about it... so don't mark it or count it as a move.
			currentLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
			mapController.setCenter(new GeoPoint(currentLocation.getLatitude(), currentLocation.getLongitude()));
			currentLocation = null;
		}
		else
		{
			// The activity was destroyed/created automatically (due to rotation), populate it
			// with the dockset loaded by the previous activity.
			NonConfigurationInstance instance = (NonConfigurationInstance)data;
			dockSet = instance.dockSet;
			currentLocation = instance.currentLocation;
			if (currentLocation != null)
			{
				GeoPoint point = new GeoPoint(currentLocation.getLatitude(), currentLocation.getLongitude());
				locationCircle.setCircleData(point, currentLocation.getAccuracy());
				locationOverlay.requestRedraw();
			}
			updateInstantly = false; // Don't trigger the update in onResume() instantly because we just rotated.
		}
		dockOverlay.updateDocks(dockSet);



	}

	@Override
	public void onResume()
	{
		super.onResume();

		// Start dock updates, but wait 30 seconds because this might have been a rotation or they might have just switched apps.
		// TODO: We really need to look at the last update time, and go 60 seconds from that!
		// Yeah just save the last update time in sharedpreferences, and work it out from there.
		triggerUpdates(updateInstantly ? 1 : 1);//30*1000);
		// Start position updates.
		startGPS();
	}

	@Override
	public void onPause()
	{
		super.onPause();

		updateInstantly = false;

		// Stop dock updates.
		stopUpdates();
		// Stop GPS.
		stopGPS();

		// TODO: Post-honeycomb, this can be moved to onStop() because the activity is not killed until after onStop() returns.
		// Save dock locations.
		saveDockLocations();
	}

	DockSet dockSet = new DockSet();

	private class NonConfigurationInstance
	{
		DockSet dockSet;
		Location currentLocation;
	}

	@Override
	public Object onRetainNonConfigurationInstance()
	{
		NonConfigurationInstance instance = new NonConfigurationInstance();
		instance.dockSet = dockSet;
		instance.currentLocation = currentLocation;
		return instance;
	}
	// Refresh the docks. This is blocking and is called in another thread.
	private void refreshDocks()
	{
		// Download the update file.
		String csv = downloadTextFile("http://api.bike-stats.co.uk/service/rest/bikestats?format=csv");
		if (csv.isEmpty())
			return;

		synchronized (dockSet)
		{
			// Decode it.
			dockSet.loadFromCSV(csv);

			// Update the map overlay. This is threadsafe so it's fine.
			if (dockSet.numDocks() > 0)
				dockOverlay.updateDocks(dockSet);
		}
	}

	// Blocking function which downloads a text (UTF-8) file and returns it as a string.
	private String downloadTextFile(String location)
	{
		try
		{
			URL url = new URL(location);
			URLConnection urlConnection = url.openConnection();
			InputStream is = new BufferedInputStream(urlConnection.getInputStream());
			final char[] buffer = new char[32768];
			StringBuilder out = new StringBuilder();
			Reader in = new InputStreamReader(is, "UTF-8");
			int read;
			do
			{
				read = in.read(buffer, 0, buffer.length);
				if (read > 0)
					out.append(buffer, 0, read);
			} while (read >= 0);

			return out.toString();

		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		return "";

		// TODO: Close is/in in a finally block. Stupid java.
	}


	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		// Handle item selection
		switch (item.getItemId())
		{
		case R.id.show_bikes:
			dockOverlay.showBikes();
			setTitle("Available Bikes");
			return true;
		case R.id.show_slots:
			dockOverlay.showSlots();
			setTitle("Available Slots");
			return true;
		case R.id.show_location:
			if (currentLocation != null)
			{
				mapController.setCenter(new GeoPoint(currentLocation.getLatitude(), currentLocation.getLongitude()));
			}
			return true;
		case R.id.refresh:
			triggerUpdates(1);
			return true;
/*		case R.id.about:
			return true;
		case R.id.preferences:
			return true;*/
		default:
			return super.onOptionsItemSelected(item);
		}
	}


	@Override
	public boolean onTrackballEvent(MotionEvent event)
	{
		// Pass through to map.
		return mapView.onTrackballEvent(event);
	}


	// Determines whether one Location reading is better than the current Location fix
	protected boolean isBetterLocation(Location location, Location currentBestLocation)
	{
		if (currentBestLocation == null)
		{
			// A new location is always better than no location
			return true;
		}

		// Check whether the new location fix is newer or older
		long timeDelta = location.getTime() - currentBestLocation.getTime();
		boolean isSignificantlyNewer = timeDelta > TWO_MINUTES;
		boolean isSignificantlyOlder = timeDelta < -TWO_MINUTES;
		boolean isNewer = timeDelta > 0;

		// If it's been more than two minutes since the current location, use the new location
		// because the user has likely moved
		if (isSignificantlyNewer)
		{
			return true;
			// If the new location is more than two minutes older, it must be worse
		}
		else if (isSignificantlyOlder)
		{
			return false;
		}

		// Check whether the new location fix is more or less accurate
		int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
		boolean isLessAccurate = accuracyDelta > 0;
		boolean isMoreAccurate = accuracyDelta < 0;
		boolean isSignificantlyLessAccurate = accuracyDelta > 200;

		// Check if the old and new location are from the same provider
		boolean isFromSameProvider = isSameProvider(location.getProvider(), currentBestLocation.getProvider());

		// Determine location quality using a combination of timeliness and accuracy
		if (isMoreAccurate)
		{
			return true;
		}
		else if (isNewer && !isLessAccurate)
		{
			return true;
		}
		else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider)
		{
			return true;
		}
		return false;
	}

	// Checks whether two providers are the same
	private boolean isSameProvider(String provider1, String provider2)
	{
		if (provider1 == null)
		{
			return provider2 == null;
		}
		return provider1.equals(provider2);
	}


	private LocationListener locationListener = new LocationListener()
	{
		public void onLocationChanged(Location location)
		{
			boolean first = currentLocation == null;
			if (isBetterLocation(location, currentLocation))
				currentLocation = location;

			GeoPoint point = new GeoPoint(currentLocation.getLatitude(), currentLocation.getLongitude());

			// If it is the first one.
			if (first)
			{
				mapController.setCenter(point);
			}
			locationCircle.setCircleData(point, currentLocation.getAccuracy());
			locationOverlay.requestRedraw();
		}

		public void onStatusChanged(String s, int i, Bundle bundle)
		{
		}

		public void onProviderEnabled(String s)
		{
		}

		public void onProviderDisabled(String s)
		{
			currentLocation = null;
			locationCircle.setCircleData(new GeoPoint(0.0, 0.0), 0.0f);
		}
	};

	private void startGPS()
	{
		locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, locationListener);
		locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 0, locationListener);
	}

	private void stopGPS()
	{
		locationManager.removeUpdates(locationListener);
	}

	private Timer updateTimer;

	// Trigger updates. Each update also triggers a succeeding one.
	// firstDelay is the delay in millisecond before the first update.
	private void triggerUpdates(int firstDelay)
	{
		if (updateTimer != null)
			updateTimer.cancel();

		updateTimer = new Timer();
		updateTimer.schedule(new UpdateDocksTask(), firstDelay);
	}

	// Stop updates.
	private void stopUpdates()
	{
		if (updateTimer != null)
			updateTimer.cancel();
	}

	class UpdateDocksTask extends TimerTask
	{
		public void run()
		{
			runOnUiThread(new Runnable()
			{
				public void run()
				{
					setProgressBarIndeterminateVisibility(true);
				}
			});
			refreshDocks();
			runOnUiThread(new Runnable()
			{
				public void run()
				{
					setProgressBarIndeterminateVisibility(false);
				}
			});
			updateTimer = new Timer();
			updateTimer.schedule(new UpdateDocksTask(), 60*1000); // Update in 60 seconds.
		}
	}



	// Return true if the SD card is accessible.
	private boolean sdOk()
	{
		String state = Environment.getExternalStorageState();
		return Environment.MEDIA_MOUNTED.equals(state);
	}

    public static final String PREFS_NAME = "CycleHirePrefs";

	private void saveDockLocations()
	{
		SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
		SharedPreferences.Editor editor = settings.edit();
		editor.putString("docks", dockSet.locationsToString());
		editor.commit();
	}

	private void restoreDockLocations()
	{
		SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
		String docks = settings.getString("docks", "");
		dockSet.locationsFromString(docks);
	}




	// Writes the map stored in the apk to the sd card.
	private boolean writeMap(String dir, String filename)
	{
		File f = new File(dir + "/" + filename);
		if (f.exists())
			return true;
		new File(dir).mkdirs();
		InputStream is = getResources().openRawResource(R.raw.london);
		if (is == null)
		{
			Log.d("CycleMapActivity", "Couldn't open internal map file!");
			return false;
		}

		try
		{
			FileOutputStream os = new FileOutputStream(f);

			byte[] buffer = new byte[32768];
			int len;
			while ((len = is.read(buffer)) > 0)
				os.write(buffer, 0, len);


			os.close();
			is.close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return false;
		}
		return true;
		// TODO: Close streams even if there is an exception (DAMN YOU JAVA!)
	}


	private void showToast(String text)
	{
		Toast toast = Toast.makeText(this, text, Toast.LENGTH_LONG);
		toast.show();
	}

/*	private String updateTime;

	private void updateTitleText()
	{
		boolean bikes = dockOverlay != null ? dockOverlay.isShowingBikes() : true;
		String
	}*/
}
