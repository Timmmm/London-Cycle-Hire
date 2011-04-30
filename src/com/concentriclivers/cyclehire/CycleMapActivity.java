package com.concentriclivers.cyclehire;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import org.mapsforge.android.maps.*;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;

public class CycleMapActivity extends MapActivity
{
	private static final String TAG = "CycleMapActivity";

	private DockSet dockSet = new DockSet();

	MapView mapView;

	/**
	 * Called when the activity is first created.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
//		setContentView(R.layout.main);

        mapView = new MapView(this);
        mapView.setClickable(true);
        mapView.setBuiltInZoomControls(true);
        mapView.setMapFile("/sdcard/london.map");
        setContentView(mapView);
		setTitle("Cycle Hire");

		test();
	}


	// API read from this file: http://api.bike-stats.co.uk/service/rest/bikestats?format=csv

	private void test()
	{
		String csv = downloadTextFile("http://api.bike-stats.co.uk/service/rest/bikestats?format=csv");

		Log.v(TAG, csv);

		if (csv.isEmpty())
			return;

		dockSet.loadFromCSV(csv);

		Log.d(TAG, "Got docks: " + dockSet.numDocks());

		ArrayList<OverlayItem> items = new ArrayList<OverlayItem>(dockSet.numDocks());

		for (int i = 0; i < dockSet.numDocks(); ++i)
		{
			String contents = "Bikes: " + dockSet.bikesFree(i) + " Slots: " + dockSet.slotsFree(i);
			double lon = dockSet.longitude(i);
			double lat = dockSet.latitude(i);
			items.add(new OverlayItem(new GeoPoint(lat, lon), dockSet.name(i), contents));
		}

		Drawable defaultMarker = getResources().getDrawable(R.drawable.marker_green);

		// create an ArrayItemizedOverlay with the default marker
		ArrayItemizedOverlay itemizedOverlay = new ArrayItemizedOverlay(defaultMarker, this);

		// add the OverlayItem to the ArrayItemizedOverlay
		itemizedOverlay.addItems(items);

		// add the ArrayItemizedOverlay to the MapView
		mapView.getOverlays().add(itemizedOverlay);

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
}
