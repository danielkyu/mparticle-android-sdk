package com.mparticle.internal;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;

import com.mparticle.MParticle;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

/**
 * Class that generates/provides and interface to the mParticle database
 * that's responsible for storing and upload messages.
 *
 * The general flow is that a message is logged the MessageTable. The MessageTable is then
 * processed and purged to create batches of messages to be uploaded, which are then inserted in the UploadTable.
 * Finally, as we successfully upload batches, we remove them from the UploadTable
 *
 */
/* package-private */class MParticleDatabase extends SQLiteOpenHelper {
    private final Context mContext;

    /**
     * The following get*Query methods were once static fields, but in order to save on app startup time, they're
     * now created as needed.
     */

    /**
     * The beginning of the delete query used to clear the uploads table after a successful upload.
     */
    static String getDeletableMessagesQuery() {
        return String.format(
                "(%s='NO-SESSION')",
                MessageTable.SESSION_ID);
    }

    private static String[] gcmColumns = {MParticleDatabase.GcmMessageTable.CONTENT_ID, MParticleDatabase.GcmMessageTable.CAMPAIGN_ID, MParticleDatabase.GcmMessageTable.EXPIRATION, MParticleDatabase.GcmMessageTable.DISPLAYED_AT};
    private static String gcmDeleteWhere = MParticleDatabase.GcmMessageTable.EXPIRATION + " < ? and " + MParticleDatabase.GcmMessageTable.DISPLAYED_AT + " > 0";

    static Cursor getGcmHistory(SQLiteDatabase database){
        return database.query(GcmMessageTable.TABLE_NAME,
                gcmColumns,
                null,
                null,
                null,
                null,
                GcmMessageTable.EXPIRATION + " desc");
    }

    static int deleteExpiredGcmMessages(SQLiteDatabase database){
        String[] deleteWhereArgs = {Long.toString(System.currentTimeMillis())};
        return database.delete(MParticleDatabase.GcmMessageTable.TABLE_NAME, gcmDeleteWhere, deleteWhereArgs);
    }

    private static String[] prepareSelection = new String[]{"_id", MessageTable.MESSAGE, MessageTable.CREATED_AT, MessageTable.STATUS, MessageTable.SESSION_ID};
    private static String prepareOrderBy = MessageTable.CREATED_AT + ", " + MessageTable.SESSION_ID + " , _id asc";

    private static String sessionHistorySelection = String.format(
            "(%s = %d) and (%s != ?)",
            MessageTable.STATUS,
            Constants.Status.UPLOADED,
            MessageTable.SESSION_ID);

    static Cursor getSessionHistory(SQLiteDatabase database, String currentSessionId){
        String[] selectionArgs = new String[]{currentSessionId};
        return database.query(
                MessageTable.TABLE_NAME,
                prepareSelection,
                sessionHistorySelection,
                selectionArgs,
                null,
                null,
                prepareOrderBy);
    }
    private static String[] readyMessages = new String[]{Integer.toString(Constants.Status.UPLOADED)};

    static Cursor getMessagesForUpload(SQLiteDatabase database){
        return database.query(
                MessageTable.TABLE_NAME,
                null,
                MessageTable.STATUS + " != ?",
                readyMessages,
                null,
                null,
                prepareOrderBy);
    }

    static Cursor getReportingMessagesForUpload(SQLiteDatabase database){
        return database.query(
                ReportingTable.TABLE_NAME,
                null,
                null,
                null,
                null,
                null,
                ReportingTable._ID + " asc");
    }

    private static final int DB_VERSION = 5;
    public static final String DB_NAME = "mparticle.db";

    interface UserAttributesTable {
        String TABLE_NAME = "attributes";
        String ATTRIBUTE_KEY = "attribute_key";
        String ATTRIBUTE_VALUE = "attribute_value";
        String IS_LIST = "is_list";
        String CREATED_AT = "created_time";
    }

    private static final String CREATE_USER_ATTRIBUTES_DDL =
            "CREATE TABLE IF NOT EXISTS " + UserAttributesTable.TABLE_NAME + " (" + BaseColumns._ID +
                    " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    UserAttributesTable.ATTRIBUTE_KEY + " COLLATE NOCASE NOT NULL, " +
                    UserAttributesTable.ATTRIBUTE_VALUE + " TEXT, " +
                    UserAttributesTable.IS_LIST + " INTEGER NOT NULL, " +
                    UserAttributesTable.CREATED_AT + " INTEGER NOT NULL " +
                    ");";

    interface BreadcrumbTable {
        String TABLE_NAME = "breadcrumbs";
        String SESSION_ID = "session_id";
        String API_KEY = "api_key";
        String MESSAGE = "message";
        String CREATED_AT = "breadcrumb_time";
        String CF_UUID = "cfuuid";
    }

    private static final String CREATE_BREADCRUMBS_DDL =
            "CREATE TABLE IF NOT EXISTS " + BreadcrumbTable.TABLE_NAME + " (" + BaseColumns._ID +
                    " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    BreadcrumbTable.SESSION_ID + " STRING NOT NULL, " +
                    BreadcrumbTable.API_KEY + " STRING NOT NULL, " +
                    BreadcrumbTable.MESSAGE + " TEXT, " +
                    BreadcrumbTable.CREATED_AT + " INTEGER NOT NULL, " +
                    BreadcrumbTable.CF_UUID + " TEXT" +
                    ");";

    interface SessionTable {
        String TABLE_NAME = "sessions";
        String SESSION_ID = "session_id";
        String API_KEY = "api_key";
        String START_TIME = "start_time";
        String END_TIME = "end_time";
        String SESSION_FOREGROUND_LENGTH = "session_length";
        String ATTRIBUTES = "attributes";
        String CF_UUID = "cfuuid";
    }

    private static final String CREATE_SESSIONS_DDL =
            "CREATE TABLE IF NOT EXISTS " + SessionTable.TABLE_NAME + " (" + BaseColumns._ID +
                    " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    SessionTable.SESSION_ID + " STRING NOT NULL, " +
                    SessionTable.API_KEY + " STRING NOT NULL, " +
                    SessionTable.START_TIME + " INTEGER NOT NULL," +
                    SessionTable.END_TIME + " INTEGER NOT NULL," +
                    SessionTable.SESSION_FOREGROUND_LENGTH + " INTEGER NOT NULL," +
                    SessionTable.ATTRIBUTES + " TEXT, " +
                    SessionTable.CF_UUID + " TEXT" +
                    ");";


    interface MessageTable extends BaseColumns{
        String TABLE_NAME = "messages";
        String SESSION_ID = "session_id";
        String API_KEY = "api_key";
        String MESSAGE = "message";
        String STATUS = "upload_status";
        String CREATED_AT = "message_time";
        String MESSAGE_TYPE = "message_type";
        String CF_UUID = "cfuuid";
    }

    private static final String CREATE_MESSAGES_DDL =
            "CREATE TABLE IF NOT EXISTS " + MessageTable.TABLE_NAME + " (" + BaseColumns._ID +
                    " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    MessageTable.SESSION_ID + " STRING NOT NULL, " +
                    MessageTable.API_KEY + " STRING NOT NULL, " +
                    MessageTable.MESSAGE + " TEXT, " +
                    MessageTable.STATUS + " INTEGER, " +
                    MessageTable.CREATED_AT + " INTEGER NOT NULL, " +
                    MessageTable.MESSAGE_TYPE + " TEXT, " +
                    MessageTable.CF_UUID + " TEXT" +
                    ");";

    interface UploadTable extends BaseColumns{
        String TABLE_NAME = "uploads";
        String API_KEY = "api_key";
        String MESSAGE = "message";
        String CREATED_AT = "message_time";
        String CF_UUID = "cfuuid";
        String SESSION_ID = "session_id";
    }

    private static final String CREATE_UPLOADS_DDL =
            "CREATE TABLE IF NOT EXISTS " + UploadTable.TABLE_NAME + " (" + BaseColumns._ID +
                    " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    UploadTable.API_KEY + " STRING NOT NULL, " +
                    UploadTable.MESSAGE + " TEXT, " +
                    UploadTable.CREATED_AT + " INTEGER NOT NULL, " +
                    UploadTable.CF_UUID + " TEXT, " +
                    UploadTable.SESSION_ID + " TEXT" +
                    ");";

    interface CommandTable {
        String TABLE_NAME = "commands";
        String URL = "url";
        String METHOD = "method";
        String POST_DATA = "post_data";
        String HEADERS = "headers";
        String CREATED_AT = "timestamp";
        String SESSION_ID = "session_id";
        String API_KEY = "api_key";
        String CF_UUID = "cfuuid";
    }

    private static final String CREATE_COMMANDS_DDL =
            "CREATE TABLE IF NOT EXISTS " + CommandTable.TABLE_NAME + " (" + BaseColumns._ID +
                    " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    CommandTable.URL + " STRING NOT NULL, " +
                    CommandTable.METHOD + " STRING NOT NULL, " +
                    CommandTable.POST_DATA + " TEXT, " +
                    CommandTable.HEADERS + " TEXT, " +
                    CommandTable.CREATED_AT + " INTEGER, " +
                    CommandTable.SESSION_ID + " TEXT, " +
                    CommandTable.API_KEY + " STRING NOT NULL, " +
                    CommandTable.CF_UUID + " TEXT" +
                    ");";

    interface GcmMessageTable {
        String CONTENT_ID = "content_id";
        String CAMPAIGN_ID = "campaign_id";
        String TABLE_NAME = "gcm_messages";
        String PAYLOAD = "payload";
        String CREATED_AT = "message_time";
        String DISPLAYED_AT = "displayed_time";
        String EXPIRATION = "expiration";
        String BEHAVIOR = "behavior";
        String APPSTATE = "appstate";
        int PROVIDER_CONTENT_ID = -1;
    }

    private static final String CREATE_GCM_MSG_DDL =
            "CREATE TABLE IF NOT EXISTS " + GcmMessageTable.TABLE_NAME + " (" + GcmMessageTable.CONTENT_ID +
                    " INTEGER PRIMARY KEY, " +
                    GcmMessageTable.PAYLOAD + " TEXT NOT NULL, " +
                    GcmMessageTable.APPSTATE + " TEXT NOT NULL, " +
                    GcmMessageTable.CREATED_AT + " INTEGER NOT NULL, " +
                    GcmMessageTable.EXPIRATION + " INTEGER NOT NULL, " +
                    GcmMessageTable.BEHAVIOR + " INTEGER NOT NULL," +
                    GcmMessageTable.CAMPAIGN_ID + " TEXT NOT NULL, " +
                    GcmMessageTable.DISPLAYED_AT + " INTEGER NOT NULL" +
                    ");";

    interface ReportingTable extends BaseColumns {
        String CREATED_AT = "report_time";
        String MODULE_ID = "module_id";
        String TABLE_NAME = "reporting";
        String MESSAGE = "message";
    }

    public static final String CREATE_REPORTING_DDL =
            "CREATE TABLE IF NOT EXISTS " + ReportingTable.TABLE_NAME + " (" + BaseColumns._ID +
                    " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    ReportingTable.MODULE_ID + " INTEGER NOT NULL, " +
                    ReportingTable.MESSAGE + " TEXT NOT NULL, " +
                    ReportingTable.CREATED_AT + " INTEGER NOT NULL" +
                    ");";

    MParticleDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        mContext = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_SESSIONS_DDL);
        db.execSQL(CREATE_MESSAGES_DDL);
        db.execSQL(CREATE_UPLOADS_DDL);
        db.execSQL(CREATE_COMMANDS_DDL);
        db.execSQL(CREATE_BREADCRUMBS_DDL);
        db.execSQL(CREATE_GCM_MSG_DDL);
        db.execSQL(CREATE_REPORTING_DDL);
        db.execSQL(CREATE_USER_ATTRIBUTES_DDL);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL(CREATE_SESSIONS_DDL);
        db.execSQL(CREATE_MESSAGES_DDL);
        db.execSQL(CREATE_UPLOADS_DDL);
        db.execSQL(CREATE_COMMANDS_DDL);
        db.execSQL(CREATE_BREADCRUMBS_DDL);
        db.execSQL(CREATE_GCM_MSG_DDL);
        db.execSQL(CREATE_REPORTING_DDL);
        db.execSQL(CREATE_USER_ATTRIBUTES_DDL);
        upgradeUserAttributes(db);
    }

    private void upgradeUserAttributes(SQLiteDatabase db) {
        SharedPreferences sharedPreferences = mContext.getSharedPreferences(Constants.PREFS_FILE, Context.MODE_PRIVATE);
        String userAttrs = sharedPreferences.getString(Constants.PrefKeys.DEPRECATED_USER_ATTRS + MParticle.getInstance().getConfigManager().getApiKey(), null);
        try {
            JSONObject userAttributes = new JSONObject(userAttrs);
            Iterator<String> iter = userAttributes.keys();
            ContentValues values;
            double time = System.currentTimeMillis();
            while (iter.hasNext()) {
                String key = iter.next();
                try {
                    Object value = userAttributes.get(key);
                    String stringValue = null;
                    if (value != null) {
                        stringValue = value.toString();
                    }
                    values = new ContentValues();
                    values.put(UserAttributesTable.ATTRIBUTE_KEY, key);
                    values.put(UserAttributesTable.ATTRIBUTE_VALUE, stringValue);
                    values.put(UserAttributesTable.IS_LIST, false);
                    values.put(UserAttributesTable.CREATED_AT, time);
                    db.insert(UserAttributesTable.TABLE_NAME, null, values);
                } catch (JSONException e) {
                }
            }

        } catch (Exception e) {
        } finally {
            sharedPreferences.edit().remove(Constants.PrefKeys.DEPRECATED_USER_ATTRS + MParticle.getInstance().getConfigManager().getApiKey()).apply();
        }
    }
}
