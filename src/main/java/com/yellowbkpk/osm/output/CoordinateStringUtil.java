package com.yellowbkpk.osm.output;

import java.text.NumberFormat;

public class CoordinateStringUtil {

	private NumberFormat numFmt;
	
	public CoordinateStringUtil () {
		super();
		
		numFmt = NumberFormat.getInstance();
		numFmt.setGroupingUsed(false);
		numFmt.setMaximumFractionDigits(7);
	}
	
	public String formatCoordinate(Double coord) {
		return numFmt.format(coord);
	}
}
