package nl.xservices.plugins;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.CalendarContract;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;

import nl.xservices.plugins.accessor.AbstractCalendarAccessor;
import nl.xservices.plugins.accessor.CalendarProviderAccessor;
import nl.xservices.plugins.accessor.LegacyCalendarAccessor;

public class Calendar extends CordovaPlugin {
    public static final String ACTION_CREATE_EVENT_WITH_OPTIONS = "createEventWithOptions";
    public static final String ACTION_CREATE_EVENT_INTERACTIVELY = "createEventInteractively";
    public static final String ACTION_DELETE_EVENT = "deleteEvent";
    public static final String ACTION_FIND_EVENT = "findEvent";
    public static final String ACTION_LIST_EVENTS_IN_RANGE = "listEventsInRange";
    public static final String ACTION_LIST_CALENDARS = "listCalendars";

    public static final Integer RESULT_CODE_CREATE = 0;
    private static final String LOG_TAG = AbstractCalendarAccessor.LOG_TAG;
    private CallbackContext callback;
    private AbstractCalendarAccessor calendarAccessor;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        callback = callbackContext;
        final boolean hasLimitedSupport = Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH;

        if (ACTION_CREATE_EVENT_WITH_OPTIONS.equals(action) || ACTION_CREATE_EVENT_INTERACTIVELY.equals(action)) {
            if (hasLimitedSupport) {
                return createEventPreICS(args);
            } else {
                return createEvent(args);
            }
        } else if (ACTION_LIST_EVENTS_IN_RANGE.equals(action)) {
            return listEventsInRange(args);
        } else if (!hasLimitedSupport && ACTION_FIND_EVENT.equals(action)) {
            return findEvents(args);
        } else if (!hasLimitedSupport && ACTION_DELETE_EVENT.equals(action)) {
            return deleteEvent(args);
        } else if (ACTION_LIST_CALENDARS.equals(action)) {
            return listCalendars();
        }
        return false;
    }

    private boolean listCalendars() throws JSONException {
        final JSONArray jsonObject = getCalendarAccessor().getActiveCalendars();
        PluginResult res = new PluginResult(PluginResult.Status.OK, jsonObject);
        callback.sendPluginResult(res);
        return true;
    }

    private boolean createEventPreICS(JSONArray args) throws JSONException {
        final JSONObject jsonFilter = args.getJSONObject(0);

        final Intent calIntent = new Intent(Intent.ACTION_EDIT)
                .setType("vnd.android.cursor.item/event")
                .putExtra("title", jsonFilter.optString("title"))
                .putExtra("beginTime", jsonFilter.optLong("startTime"))
                .putExtra("endTime", jsonFilter.optLong("endTime"))
                .putExtra("hasAlarm", 1)
                .putExtra("allDay", AbstractCalendarAccessor.isAllDayEvent(new Date(jsonFilter.optLong("startTime")), new Date(jsonFilter.optLong("endTime"))));

        // optional fields
        if (!jsonFilter.isNull("location")) {
            calIntent.putExtra("eventLocation", jsonFilter.optString("location"));
        }
        if (!jsonFilter.isNull("notes")) {
            calIntent.putExtra("description", jsonFilter.optString("notes"));
        }

        this.cordova.startActivityForResult(this, calIntent, RESULT_CODE_CREATE);

        return true;
    }

    private AbstractCalendarAccessor getCalendarAccessor() {
        if (this.calendarAccessor == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                Log.d(LOG_TAG, "Initializing calendar plugin");
                this.calendarAccessor = new CalendarProviderAccessor(this.cordova);
            } else {
                Log.d(LOG_TAG, "Initializing legacy calendar plugin");
                this.calendarAccessor = new LegacyCalendarAccessor(this.cordova);
            }
        }
        return this.calendarAccessor;
    }

    private boolean deleteEvent(JSONArray args) {
        if (args.length() == 0) {
            System.err.println("Exception: No Arguments passed");
        } else {
            try {
                JSONObject jsonFilter = args.getJSONObject(0);
                boolean deleteResult = getCalendarAccessor().deleteEvent(
                        null,
                        jsonFilter.optLong("startTime"),
                        jsonFilter.optLong("endTime"),
                        jsonFilter.optString("title"),
                        jsonFilter.optString("location"));
                PluginResult res = new PluginResult(PluginResult.Status.OK, deleteResult);
                res.setKeepCallback(true);
                callback.sendPluginResult(res);
                return true;
            } catch (JSONException e) {
                System.err.println("Exception: " + e.getMessage());
            }
        }
        return false;
    }

    private boolean findEvents(JSONArray args) {
        if (args.length() == 0) {
            System.err.println("Exception: No Arguments passed");
        }
        try {
            JSONObject jsonFilter = args.getJSONObject(0);
            JSONArray jsonEvents = getCalendarAccessor().findEvents(
                    jsonFilter.isNull("title") ? null : jsonFilter.optString("title"),
                    jsonFilter.isNull("location") ? null : jsonFilter.optString("location"),
                    jsonFilter.optLong("startTime"),
                    jsonFilter.optLong("endTime"));

            PluginResult res = new PluginResult(PluginResult.Status.OK, jsonEvents);
            res.setKeepCallback(true);
            callback.sendPluginResult(res);
            return true;

        } catch (JSONException e) {
            System.err.println("Exception: " + e.getMessage());
        }
        return false;
    }

    private boolean createEvent(JSONArray args) {
        try {
            final JSONObject argObject = args.getJSONObject(0);

            Intent intent = new Intent(Intent.ACTION_INSERT)
                    .setData(CalendarContract.Events.CONTENT_URI)
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, argObject.getLong("startTime"))
                    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, argObject.getLong("endTime"))
                    .putExtra(CalendarContract.Events.TITLE, argObject.getString("title"))
                    .putExtra(CalendarContract.Events.DESCRIPTION, argObject.isNull("notes") ? null : argObject.getString("notes"))
                    .putExtra(CalendarContract.Events.EVENT_LOCATION, argObject.isNull("location") ? null : argObject.getString("location"))
                    .putExtra(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY);

            this.cordova.getActivity().startActivity(intent);
            callback.success("");

            return true;
        } catch (Exception e) {
            System.err.println("Exception: " + e.getMessage());
            callback.error("Unable to add event");
        }
        return false;
    }

    private boolean listEventsInRange(JSONArray args) {

        if (args.length() == 0) {
            System.err.println("Exception: No Arguments passed");
        }
        try {
            JSONObject jsonFilter = args.getJSONObject(0);
            JSONArray jsonEvents = getCalendarAccessor().findEvents(null, null,
                    jsonFilter.optLong("startTime"),
                    jsonFilter.optLong("endTime"));

            PluginResult res = new PluginResult(PluginResult.Status.OK, jsonEvents);
            res.setKeepCallback(true);
            callback.sendPluginResult(res);
            return true;

        } catch (JSONException e) {
            System.err.println("Exception: " + e.getMessage());
        }
        return false;
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RESULT_CODE_CREATE) {
            if (resultCode == Activity.RESULT_OK || resultCode == Activity.RESULT_CANCELED) {
                // resultCode may be 0 (RESULT_CANCELED) even when it was created, so passing nothing is the clearest option here
                callback.success();
            }
        } else {
            callback.error("Unable to add event (" + resultCode + ").");
        }
    }
}
