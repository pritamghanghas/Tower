package org.droidplanner.android.activities;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.MAVLink.Messages.ardupilotmega.msg_global_position_int;
import com.google.common.collect.Lists;

import org.droidplanner.R;
import org.droidplanner.android.activities.helpers.SuperUI;
import org.droidplanner.android.activities.interfaces.OnLocatorListListener;
import org.droidplanner.android.dialogs.openfile.OpenFileDialog;
import org.droidplanner.android.dialogs.openfile.OpenTLogDialog;
import org.droidplanner.android.fragments.LocatorListFragment;
import org.droidplanner.android.fragments.LocatorMapFragment;
import org.droidplanner.android.utils.file.IO.TLogReader;
import org.droidplanner.core.helpers.coordinates.Coord2D;
import org.droidplanner.core.helpers.geoTools.GeoTools;

import java.util.ArrayList;
import java.util.List;

/**
 * This implements the map locator activity. The map locator activity allows the user to find
 * a lost drone using last known GPS positions from the tlogs.
 */
public class LocatorActivity extends SuperUI implements OnLocatorListListener, LocationListener {

    private static final String STATE_LASTSELECTEDPOSITION = "STATE_LASTSELECTEDPOSITION";

    private final static List<msg_global_position_int> lastPositions = new ArrayList<msg_global_position_int>();

    /*
    View widgets.
     */
    private LocationManager locationManager;
    private FragmentManager fragmentManager;

    private LocatorMapFragment locatorMapFragment;
	private LocatorListFragment locatorListFragment;
    private LinearLayout statusView;
    private FrameLayout lowerWidgetContainer;
    private TextView latView, lonView, distanceView, azimuthView;

    private msg_global_position_int selectedMsg;
    private Coord2D lastGCSPosition;
    private float lastGCSBearingTo = Float.MAX_VALUE;
    private double lastGCSAzimuth = Double.MAX_VALUE;


    public List<msg_global_position_int> getLastPositions() {
        return lastPositions;
    }

    @Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_locator);

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        fragmentManager = getSupportFragmentManager();

		locatorMapFragment = ((LocatorMapFragment) fragmentManager
				.findFragmentById(R.id.mapFragment));
		locatorListFragment = (LocatorListFragment) fragmentManager
				.findFragmentById(R.id.locatorListFragment);

        statusView = (LinearLayout) findViewById(R.id.statusView);
        latView = (TextView) findViewById(R.id.latView);
        lonView = (TextView) findViewById(R.id.lonView);
        distanceView = (TextView) findViewById(R.id.distanceView);
        azimuthView = (TextView) findViewById(R.id.azimuthView);

        lowerWidgetContainer = (FrameLayout) findViewById(R.id.lowerWidgetContainer);

        // attach click listener to zoom button
        final ImageButton zoomToFitButton = (ImageButton) findViewById(R.id.zoom_to_fit_button);
        zoomToFitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                locatorMapFragment.zoomToFit();
            }
        });

        // clear prev state if this is a fresh start
        if(savedInstanceState == null) {
            // fresh start
            lastPositions.clear();
        }
	}

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        final int lastSelectedPosition = lastPositions.indexOf(selectedMsg);
        outState.putInt(STATE_LASTSELECTEDPOSITION, lastSelectedPosition);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        final int lastSelectedPosition = savedInstanceState.getInt(STATE_LASTSELECTEDPOSITION, -1);
        if(lastSelectedPosition != -1 && lastSelectedPosition < lastPositions.size())
            setSelectedMsg(lastPositions.get(lastSelectedPosition));
    }

    @Override
    public void onResume(){
        super.onResume();

        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this, null);
    }

    @Override
    protected void onPause() {
        super.onPause();

        locationManager.removeUpdates(this);
    }

    @Override
    public void onStart(){
        super.onStart();
    }

    @Override
    public void onStop(){
        super.onStop();
    }

	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        getMenuInflater().inflate(R.menu.menu_locator, menu);
        return true;
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_open_tlog_file:
                openLogFile();
                return true;

            default:
                return super.onMenuItemSelected(featureId, item);
        }
    }

    private void openLogFile() {
        OpenFileDialog tlogDialog = new OpenTLogDialog() {
            @Override
            public void tlogFileLoaded(TLogReader reader) {
                loadLastPositions(reader.getLogEvents());
                locatorMapFragment.zoomToFit();
            }
        };
        tlogDialog.openDialog(this);
    }

    /*
    Copy all messages with non-zero coords -> lastPositions and reverse the list (most recent first)
     */
    private void loadLastPositions(List<TLogReader.Event> logEvents) {
        final ArrayList<msg_global_position_int> positions = new ArrayList<msg_global_position_int>();
        for (TLogReader.Event event : logEvents) {
            final msg_global_position_int message = (msg_global_position_int) event.getMavLinkMessage();
            if(message.lat != 0 || message.lon != 0)
                positions.add(message);
        }

        setSelectedMsg(null);
        lastPositions.clear();
        lastPositions.addAll(Lists.reverse(positions));
        locatorListFragment.notifyDataSetChanged();

        updateInfo();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		updateMapPadding();
	}

	private void updateMapPadding() {
        final int topPadding = statusView.getTop();
        final int leftPadding = statusView.getRight();
        int bottomPadding = 0;

        if(lastPositions.size() > 0) {
            bottomPadding = locatorListFragment.getView().getHeight();
        }

        locatorMapFragment.setMapPadding(leftPadding, topPadding, 0, bottomPadding);
	}

	@Override
	public void onBackPressed() {
		super.onBackPressed();
		locatorMapFragment.saveCameraPosition();
	}

    @Override
    public void onItemClick(msg_global_position_int msg) {
        setSelectedMsg(msg);

        locatorMapFragment.zoomToFit();
        updateInfo();
    }

    public void setSelectedMsg(msg_global_position_int msg) {
        selectedMsg = msg;

        final Coord2D msgCoord;
        if(msg != null)
            msgCoord = coordFromMsgGlobalPositionInt(selectedMsg);
        else
            msgCoord = new Coord2D(0, 0);
        locatorMapFragment.updateLastPosition(msgCoord);
    }

    private void updateInfo() {
        if(selectedMsg != null) {
            // coords
            final Coord2D msgCoord = coordFromMsgGlobalPositionInt(selectedMsg);

            // distance
            if(lastGCSPosition == null || lastGCSPosition.isEmpty()) {
                // unknown
                distanceView.setText("");
                azimuthView.setText("");
            } else {
                String distance = String.format("Distance: %.01fm", GeoTools.getDistance(lastGCSPosition, msgCoord).valueInMeters());
                if(lastGCSBearingTo != Float.MAX_VALUE) {
                    final String bearing = String.format(" @ %.0f°", lastGCSBearingTo);
                    distance += bearing;
                }
                distanceView.setText(distance);

                if(lastGCSAzimuth != Double.MAX_VALUE) {
                    final String azimuth = String.format("Heading: %.0f°", lastGCSAzimuth);
                    azimuthView.setText(azimuth);
                }
            }

            latView.setText(String.format("Latitude: %f°", msgCoord.getLat()));
            lonView.setText(String.format("Longitude: %f°", msgCoord.getLng()));
        } else {
            latView.setText("");
            lonView.setText("");
            distanceView.setText("");
            azimuthView.setText("");
        }
    }

    private static Coord2D coordFromMsgGlobalPositionInt(msg_global_position_int msg) {
        double lat = msg.lat;
        lat /= 1E7;

        double lon = msg.lon;
        lon /= 1E7;

        return new Coord2D(lat, lon);
    }

    @Override
    public void onLocationChanged(Location location) {
        lastGCSPosition = new Coord2D(location.getLatitude(), location.getLongitude());
        lastGCSAzimuth = location.getBearing();

        if(selectedMsg != null) {
            final Coord2D msgCoord = coordFromMsgGlobalPositionInt(selectedMsg);

            final Location target = new Location(location);
            target.setLatitude(msgCoord.getLat());
            target.setLongitude(msgCoord.getLng());

            lastGCSBearingTo = Math.round(location.bearingTo(target));
            lastGCSBearingTo = (lastGCSBearingTo + 360) % 360;
        } else {
            lastGCSBearingTo = Float.MAX_VALUE;
        }

        updateInfo();
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // NOP
    }

    @Override
    public void onProviderEnabled(String provider) {
        // NOP
    }

    @Override
    public void onProviderDisabled(String provider) {
        // NOP
    }
}
