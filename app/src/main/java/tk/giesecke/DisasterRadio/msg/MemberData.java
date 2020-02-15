package tk.giesecke.DisasterRadio.msg;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Marker;

import static tk.giesecke.DisasterRadio.TerminalFragment.mapView;

/**
 * MemberData class holds the data for a member that has joined DisasterRadio chat.
 */
public class MemberData {
	private String name;
	private String color;
	private GeoPoint point;
	private Marker marker;
	private boolean latLngValid;
	private final Context appContext;

	public MemberData(Context thisContext, String name, String color) {
		this.name = name;
		this.color = color;
		this.point = new GeoPoint(0d,0d);
		this.latLngValid = false;
		this.marker = null;
		this.appContext = thisContext;
	}

	public String getName() {
		return name;
	}

	public String getColor() {
		return color;
	}

	public void setData(String name, String color) {
		this.name = name;
		this.color = color;
	}

	public boolean isCoordValid() {
		return this.latLngValid;
	}

	private BitmapDrawable writeOnDrawable(int drawableId, String text){

		Bitmap bm = BitmapFactory.decodeResource(appContext.getResources(), drawableId).copy(Bitmap.Config.ARGB_8888, true);

		Paint paint = new Paint();
		paint.setStyle(Paint.Style.FILL);
		paint.setColor(Color.RED);
		paint.setTextSize(30);
		paint.setFakeBoldText(true);

		Rect bounds = new Rect();
		paint.getTextBounds(text, 0, text.length(), bounds);
		float startPoint = (bm.getWidth()/2)-(bounds.width()/2);
		if ((bm.getWidth() - bounds.width()) <= 0) {
			// Text too long, make it smaller
			paint.setTextSize(20);
			paint.getTextBounds(text, 0, text.length(), bounds);
			startPoint = 5;
		}
		Canvas canvas = new Canvas(bm);
		canvas.drawText(text, startPoint, bm.getHeight()/2, paint);

		return new BitmapDrawable(this.appContext.getResources(), bm);
	}

	public Marker setCoords(GeoPoint point) {
		this.point = point;
		this.latLngValid = true;
		if (this.marker == null) {
			this.marker = new Marker(mapView);
		}
		this.marker.setPosition(this.point);
		this.marker.setTitle(this.name);

		this.marker.setIcon(writeOnDrawable(org.osmdroid.library.R.drawable.bonuspack_bubble, this.name));

		this.marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
		return this.marker;
	}

	public GeoPoint getCoords() {
		return this.point;
	}

	public Marker getMarker() {
		return this.marker;
	}
}
