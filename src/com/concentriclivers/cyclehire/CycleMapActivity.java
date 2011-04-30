package com.concentriclivers.cyclehire;

import android.location.Location;
import android.location.LocationListener;
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

	/**
	 * Called when the activity is first created.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        mapView = new MapView(this);
        mapView.setClickable(true);
        mapView.setBuiltInZoomControls(true);
        mapView.setMapFile("/sdcard/london.map");
        setContentView(mapView);


		configureMapView();

		dockOverlay = new DockOverlay(this);
		setTitle("Cycle Hire - Free Bikes");

		mapView.getOverlays().add(dockOverlay);

		triggerUpdates();
	}


	// API read from this file: http://api.bike-stats.co.uk/service/rest/bikestats?format=csv

	private void refreshDocks()
	{

		String csv = downloadTextFile("http://api.bike-stats.co.uk/service/rest/bikestats?format=csv");
		if (csv.isEmpty())
			return;
		DockSet dockSet = new DockSet();

		dockSet.loadFromCSV(csv);
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
			return true;
		case R.id.refresh:
			triggerUpdates();
			return true;
		case R.id.about:
			return true;
		case R.id.preferences:
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}


	@Override
	public boolean onTrackballEvent(MotionEvent event)
	{
		return mapView.onTrackballEvent(event);
	}


	private void configureMapView()
	{
		// configure the MapView and activate the zoomLevel buttons
		mapView.setClickable(true);
		mapView.setBuiltInZoomControls(true);
		mapView.setFocusable(true);

		// set the localized text fields
		mapView.setText(MapView.TextField.KILOMETER, "km");
		mapView.setText(MapView.TextField.METER, "m");
	}

	private LocationListener locationListener;

	ArrayCircleOverlay circleOverlay;
	OverlayCircle overlayCircle;
/*
	private void enableGPS()
	{
		circleOverlay = new ArrayCircleOverlay(this.circleOverlayFill, this.circleOverlayOutline, this);
		overlayCircle = new OverlayCircle();
		circleOverlay.addCircle(this.overlayCircle);
		mapView.getOverlays().add(this.circleOverlay);


		locationListener = new LocationListener()
		{
			private boolean first = true;
			@Override
			public void onLocationChanged(Location location)
			{
				GeoPoint point = new GeoPoint(location.getLatitude(), location.getLongitude());

				// If it is the first one.
				if (first)
				{
					mapController.setCenter(point);
					first = false;
				}
				overlayCircle.setCircleData(point, location.getAccuracy());
			}

			@Override
			public void onProviderDisabled(String provider)
			{
				disableFollowGPS(false);
				showDialog(DIALOG_GPS_DISABLED);
			}

			@Override
			public void onProviderEnabled(String provider)
			{
				// do nothing
			}

			@Override
			public void onStatusChanged(String provider, int status, Bundle extras)
			{
				if (status == LocationProvider.AVAILABLE)
				{
					AdvancedMapViewer.this.gpsView.setImageResource(R.drawable.stat_sys_gps_on);
				}
				else if (status == LocationProvider.OUT_OF_SERVICE)
				{
					AdvancedMapViewer.this.gpsView.setImageResource(R.drawable.stat_sys_gps_acquiring);
				}
				else
				{
					// must be TEMPORARILY_UNAVAILABLE
					AdvancedMapViewer.this.gpsView.setImageResource(R.anim.gps_animation);
					((AnimationDrawable) AdvancedMapViewer.this.gpsView.getDrawable()).start();
				}
			}
		};

		this.locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this.locationListener);
		this.gpsView.setImageResource(R.drawable.stat_sys_gps_acquiring);
		this.gpsView.setVisibility(View.VISIBLE);
		this.gpsView.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				disableFollowGPS(true);
			}
		});
	}*/

	private Timer updateTimer;

	// Triggers an immediate update. Each update also triggers a succeeding one.
	private void triggerUpdates()
	{
		if (updateTimer != null)
			updateTimer.cancel();

		updateTimer = new Timer();
		updateTimer.schedule(new UpdateDocksTask(), 1);
	}

	class UpdateDocksTask extends TimerTask
	{
		public void run()
		{
			runOnUiThread(new Runnable() { public void run() { setProgressBarIndeterminateVisibility(true); } });
			refreshDocks();
			runOnUiThread(new Runnable() { public void run() { setProgressBarIndeterminateVisibility(false); } });
			updateTimer = new Timer();
			updateTimer.schedule(new UpdateDocksTask(), 60*1000); // Update in 60 seconds.
		}
	}
}
