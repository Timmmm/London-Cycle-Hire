package com.concentriclivers.cyclehire;

import android.graphics.Color;
import android.graphics.Paint;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.*;
import org.mapsforge.android.maps.*;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
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

	// Called when the activity is first created.
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		// Request throbber. Must be done before setContentView().
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

		// Create the map view.
        mapView = new MapView(this);
        mapView.setClickable(true);
        mapView.setBuiltInZoomControls(true);
        mapView.setMapFile("/sdcard/london.map");
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
		setTitle("Cycle Hire - Available Bikes");

		// Initialise service references.
		mapController = mapView.getController();
		locationManager = (LocationManager)getSystemService(LOCATION_SERVICE);

		// Got to last location.
		currentLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
		mapController.setCenter(new GeoPoint(currentLocation.getLatitude(), currentLocation.getLongitude()));
	
	}

	@Override
	public void onResume()
	{
		super.onResume();

		// Start dock updates.
		triggerUpdates();
		// Start position updates.
		startGPS();
	}

	@Override
	public void onPause()
	{
		super.onPause();

		// Stop dock updates.
		stopUpdates();
		// Stop GPS.
		stopGPS();
	}

	// Refresh the docks. This is blocking and is called in another thread.
	private void refreshDocks()
	{
		// Download the update file.
		String csv = downloadTextFile("http://api.bike-stats.co.uk/service/rest/bikestats?format=csv");
		if (csv.isEmpty())
			return;

		DockSet dockSet = new DockSet();

		// Decode it.
		dockSet.loadFromCSV(csv);

		// Update the map overlay. This is threadsafe so it's fine.
		if (dockSet.numDocks() > 0)
			dockOverlay.updateDocks(dockSet);
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
			setTitle("Cycle Hire - Available Bikes");
			return true;
		case R.id.show_slots:
			dockOverlay.showSlots();
			setTitle("Cycle Hire - Available Slots");
			return true;
		case R.id.show_location:
			if (currentLocation != null)
			{
				mapController.setCenter(new GeoPoint(currentLocation.getLatitude(), currentLocation.getLongitude()));
			}
			return true;
		case R.id.refresh:
			triggerUpdates();
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
		private boolean first = true;

		public void onLocationChanged(Location location)
		{
			if (isBetterLocation(location, currentLocation))
				currentLocation = location;

			GeoPoint point = new GeoPoint(currentLocation.getLatitude(), currentLocation.getLongitude());

			// If it is the first one.
			if (first)
			{
				mapController.setCenter(point);
				first = false;
			}
			locationCircle.setCircleData(point, currentLocation.getAccuracy());
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

	// Triggers an immediate update. Each update also triggers a succeeding one.
	private void triggerUpdates()
	{
		if (updateTimer != null)
			updateTimer.cancel();

		updateTimer = new Timer();
		updateTimer.schedule(new UpdateDocksTask(), 1);
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
}
