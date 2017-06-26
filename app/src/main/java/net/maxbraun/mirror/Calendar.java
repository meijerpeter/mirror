package net.maxbraun.mirror;


import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.content.ContentUris;
import android.content.ContentResolver;
import android.util.Log;
import android.widget.ImageView;


/**
 * Created by Peter Meijer on 26/06/2017.
 */

public class Calendar extends DataUpdater<List<String>> {

    private static final String TAG = Calendar.class.getSimpleName();

    public static final String ENABLED = "ON";

    public static final int ICON = android.R.drawable.ic_menu_my_calendar;

    private Context context;

    /**
     * The time in milliseconds between API calls to update the calendar.
     */
    private static final long UPDATE_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(15);


    public Calendar(UpdateListener<List<String>> updateListener) {
        super(updateListener, UPDATE_INTERVAL_MILLIS);
    }

    public Calendar(Context context, UpdateListener<List<String>> updateListener) {
        super(updateListener, UPDATE_INTERVAL_MILLIS);

        this.context = context;
    }

    @Override
    protected String getTag() {
        return TAG;
    }


    @Override
    protected List<String> getData() {

        List<String> eventDetails = new ArrayList<String>();

        Log.d(TAG, "Starting to look into the calendars...");

        Cursor cursor;
        ContentResolver contentResolver = context.getContentResolver();
        final String[] colsToQuery = new String[]
                {CalendarContract.Instances.CALENDAR_ID, CalendarContract.Instances.TITLE,
                        CalendarContract.Instances.DESCRIPTION, CalendarContract.Instances.BEGIN,
                        CalendarContract.Instances.END, CalendarContract.Instances.EVENT_LOCATION,
                        CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.EVENT_TIMEZONE};

        long now = System.currentTimeMillis();
        long tomorrow = now + 2*(86400l * 1000l);
        now -= (86400l * 1000l);  // to deal with timezone issues, get several days' worth and then remove unneeded events

        String selection = CalendarContract.Instances.BEGIN + " >= " + now + " and " + CalendarContract.Instances.BEGIN
                + " <= " + tomorrow + " and " + CalendarContract.Instances.VISIBLE + " = 1";
        String sort = CalendarContract.Instances.BEGIN + " ASC";

        Uri.Builder eventsUriBuilder = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(eventsUriBuilder, Long.MIN_VALUE);
        ContentUris.appendId(eventsUriBuilder, Long.MAX_VALUE);
        Uri eventsUri = eventsUriBuilder.build();
        cursor = context.getContentResolver().query(eventsUri, colsToQuery, selection, null, sort);
        List<EventDetail> deets = new ArrayList<EventDetail>();

        // pull all the events and put them in a list of eventdetails
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                while (cursor.moveToNext()) {
                    String title = cursor.getString(1);
                    if (title.length() > 49)
                        title = title.substring(0,44)+"...";
                    java.util.Calendar startTime = java.util.Calendar.getInstance();
                    java.util.Calendar endTime = java.util.Calendar.getInstance();

                    // adjust for timezone because, for some weird reason, all-day events are in GMT regardless of your tz
                    TimeZone eventTz = TimeZone.getTimeZone(cursor.getString(7));
                    TimeZone localTz = TimeZone.getDefault();
                    int diffTz = localTz.getOffset(new Date().getTime()) - eventTz.getOffset(new Date().getTime());
                    startTime.setTimeInMillis(cursor.getLong(3) - diffTz);
                    endTime.setTimeInMillis(cursor.getLong(4) - diffTz);
                    deets.add(new EventDetail(startTime, endTime, title));
                }
            }
            cursor.close();
        }
        // Now that the timezones are adjusted, re-order and prepare display strings for the events
        for (int i = 1; i < deets.size(); i++) {
            if (deets.get(i).start.before(deets.get(i-1).start)) {
                EventDetail e = deets.get(i);
                deets.remove(i);
                deets.add(i-1, e);
                i = 1;
            }
        }

        // Build a list of strings for the mainactivity routine to display
        for (EventDetail d : deets) {
            eventDetails.add(d.toString());
        }
        return eventDetails;

    }

    class EventDetail { // utility class to hold details of an event

        java.util.Calendar start, end;
        String name;

        public EventDetail(java.util.Calendar s, java.util.Calendar e, String n) {
            start = s; end = e; name = n;
        }

        private String displayStartTime(java.util.Calendar c) {
            //Make sure all the timestamps are equally formatted
            return new SimpleDateFormat("E HH:mm", Locale.US).format(c.getTime());
        }

        private String displayEndTime(java.util.Calendar c) {
            //Make sure all the timestamps are equally formatted
            return new SimpleDateFormat("HH:mm", Locale.US).format(c.getTime());
        }

        public String toString() {

            // Assume any 24-hour appointment is an all-day reminder and remove the time.
            if (end.getTimeInMillis() >= start.getTimeInMillis() + 86400l*1000l) {
                return "All day: " + name;
                //If start and end are the same, display only the start time
            } else if (end.getTimeInMillis() == start.getTimeInMillis()) {
                return displayStartTime(start) + ": " + name;
                //Default display
            } else {
                return displayStartTime(start) + " - " + displayEndTime(end) + ": " + name;
            }
        }
    }

}
