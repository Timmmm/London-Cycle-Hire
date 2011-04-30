package com.concentriclivers.cyclehire;

import android.util.Log;

import java.util.ArrayList;

public class DockSet
{
	public DockSet()
	{


	}

	// Split up a CSV line, and it also handles quoted strings, e.g   foo,bar,"baz,biz",bang
	private String[] splitCSV(String line)
	{
		ArrayList<String> cells = new ArrayList<String>();
		int a = 0;
		boolean instring = false;
		for (int i = 0; i < line.length(); ++i)
		{
			if (line.charAt(i) == ',' && !instring)
			{
				cells.add(line.substring(a, i));
				a = i + 1;
			}
			else if (line.charAt(i) == '"')
			{
				instring = !instring;
			}
		}
		if (a <= line.length()) // TODO: Double check this.
			cells.add(line.substring(a));

		return cells.toArray(new String[cells.size()]);
	}

	// Read the locations of all the docks and their names, and statuses
	public boolean loadFromCSV(String file)
	{
		docks.clear();
		// The first line is the headings:

		// ID,Name,Latitude,Longitude,BikesAvailable,EmptySlots,Installed,Locked,Temporary,UpdateTime

		String[] lines = file.split("\\r?\\n");

		for (int i = 1; i < lines.length; ++i)
		{
			String[] vals = splitCSV(lines[i]);
			if (vals.length != 10)
				continue;

			Dock d = new Dock();
			d.id = Integer.parseInt(vals[0]);
			d.name = vals[1];
			d.latitude = Double.parseDouble(vals[2]);
			d.longitude = Double.parseDouble(vals[3]);
			d.bikes = Integer.parseInt(vals[4]);
			d.slots = Integer.parseInt(vals[5]);
			d.capacity = 0;

			docks.add(d);
		}

		return !docks.isEmpty();
	}

	// Number of docks.
	public int numDocks()
	{
		return docks.size();
	}

	// The ID of a dock.
	public int id(int index)
	{
		return docks.get(index).id;
	}

	// Number of bikes it can hold.
	public int capacity(int index)
	{
		return docks.get(index).capacity;
	}

	public double longitude(int index)
	{
		return docks.get(index).longitude;
	}

	public double latitude(int index)
	{
		return docks.get(index).latitude;
	}

	public String name(int index)
	{
		return docks.get(index).name;
	}

	public int slotsFree(int index)
	{
		return docks.get(index).slots;
	}

	public int bikesFree(int index)
	{
		return docks.get(index).bikes;
	}

	// Whether the dock is open or not -- sometimes they close.
	public boolean open(int index)
	{
		return true;
	}

	private class Dock
	{
		public int id;
		public String name;
		public double longitude;
		public double latitude;
		public int capacity;
		public int slots;
		public int bikes;
	}

	private ArrayList<Dock> docks = new ArrayList<Dock>();
}
