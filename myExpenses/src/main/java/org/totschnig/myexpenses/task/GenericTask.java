package org.totschnig.myexpenses.task;

import static org.totschnig.myexpenses.provider.DatabaseConstants.KEY_PLANID;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;
import android.provider.CalendarContract;

import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.model.Plan;
import org.totschnig.myexpenses.preference.PrefKey;
import org.totschnig.myexpenses.provider.DatabaseConstants;
import org.totschnig.myexpenses.provider.TransactionProvider;

import java.io.Serializable;

/**
 * Note that we need to check if the callbacks are null in each method in case
 * they are invoked after the Activity's and Fragment's onDestroy() method
 * have been called.
 */
@Deprecated
public class GenericTask<T> extends AsyncTask<T, Void, Object> {
  private final TaskExecutionFragment taskExecutionFragment;
  private final int mTaskId;
  private final Serializable mExtra;

  public GenericTask(TaskExecutionFragment taskExecutionFragment, int taskId, Serializable extra) {
    this.taskExecutionFragment = taskExecutionFragment;

    mTaskId = taskId;
    mExtra = extra;
  }

  @Override
  protected void onPreExecute() {
    if (this.taskExecutionFragment.mCallbacks != null) {
      this.taskExecutionFragment.mCallbacks.onPreExecute();
    }
  }

  /**
   * Note that we do NOT call the callback object's methods directly from the
   * background thread, as this could result in a race condition.
   */
  @Override
  protected Object doInBackground(T... ids) {
    final MyApplication application = MyApplication.getInstance();
    final Context context = application.getWrappedContext();
    ContentResolver cr = context.getContentResolver();
    ContentValues values;
    switch (mTaskId) {
      case TaskExecutionFragment.TASK_INSTANTIATE_PLAN:
        return Plan.getInstanceFromDb((Long) ids[0]);
      case TaskExecutionFragment.TASK_SWAP_SORT_KEY:
        cr.update(
            TransactionProvider.ACCOUNTS_URI
                .buildUpon()
                .appendPath(TransactionProvider.URI_SEGMENT_SWAP_SORT_KEY)
                .appendPath((String) ids[0])
                .appendPath((String) ids[1])
                .build(),
            null, null, null);
        return null;
      case TaskExecutionFragment.TASK_UPDATE_SORT_KEY:
        values = new ContentValues();
        values.put(DatabaseConstants.KEY_SORT_KEY, (Integer) mExtra);
        cr.update(
            TransactionProvider.ACCOUNTS_URI.buildUpon().appendPath(String.valueOf(ids[0])).build(),
            values, null, null);
        return null;
      case TaskExecutionFragment.TASK_REPAIR_PLAN:
        String calendarId = PrefKey.PLANNER_CALENDAR_ID.getString("-1");
        if (calendarId.equals("-1")) {
          return false;
        }
        values = new ContentValues();
        for (String uuid : (String[]) ids) {
          Cursor eventCursor = cr.query(CalendarContract.Events.CONTENT_URI, new String[]{CalendarContract.Events._ID},
                  CalendarContract.Events.CALENDAR_ID + " = ? AND " + CalendarContract.Events.DESCRIPTION
                  + " LIKE ?", new String[]{calendarId,
                  "%" + uuid + "%"}, null);
          if (eventCursor != null) {
            if (eventCursor.moveToFirst()) {
              values.put(KEY_PLANID, eventCursor.getLong(0));
              cr.update(TransactionProvider.TEMPLATES_URI, values,
                  DatabaseConstants.KEY_UUID + " = ?",
                  new String[]{uuid});
            }
            eventCursor.close();
          }
        }
        return true;
    }
    return null;
  }

  @Override
  protected void onProgressUpdate(Void... ignore) {
    /*
     * if (mCallbacks != null) { mCallbacks.onProgressUpdate(ignore[0]); }
     */
  }

  @Override
  protected void onCancelled() {
    if (this.taskExecutionFragment.mCallbacks != null) {
      this.taskExecutionFragment.mCallbacks.onCancelled();
    }
  }

  @Override
  protected void onPostExecute(Object result) {
    if (this.taskExecutionFragment.mCallbacks != null) {
      this.taskExecutionFragment.mCallbacks.onPostExecute(mTaskId, result);
    }
  }
}