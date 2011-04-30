package com.concentriclivers.cyclehire;

import android.content.Context;
import android.graphics.drawable.Drawable;
import org.mapsforge.android.maps.ArrayItemizedOverlay;

/**
 * Map overlay that shows the docks and the number of slots/bikes left.
 */
public class DockOverlay extends ArrayItemizedOverlay
{

	public DockOverlay(Drawable defaultMarker, Context context)
	{
		super(defaultMarker, context);
	}

	
}
