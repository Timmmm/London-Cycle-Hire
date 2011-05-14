package com.concentriclivers.cyclehire;

import android.graphics.drawable.Drawable;
import org.mapsforge.android.maps.GeoPoint;
import org.mapsforge.android.maps.OverlayItem;

public class DockOverlayItem extends OverlayItem
{
	// -1 means unknown, in which case a white icon is shown.
	public int bikes;
	public int slots;

	public DockOverlayItem(GeoPoint point, String name, int bikes, int slots, Drawable marker)
	{
		super(point, name, "Bikes: " + bikes + " Slots: " + slots, marker);
		this.bikes = bikes;
		this.slots = slots;
	}
}
