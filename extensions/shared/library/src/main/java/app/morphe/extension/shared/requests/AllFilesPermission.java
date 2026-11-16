package app.morphe.extension.shared.requests;

import android.app.Activity;
import android.app.Application;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;

public final class AllFilesPermission {
    private AllFilesPermission() {}
    public static void request(Activity activity, Runnable confirmedAction) {
        new AlertDialog.Builder(activity).setTitle("Move local media to Locked Folder")
            .setMessage("This move needs access to files in shared storage. Android's All files access also permits access to documents there. You can revoke it afterwards in Settings. Return here to continue this move once.")
            .setNegativeButton("Cancel", null).setPositiveButton("Open Settings", (dialog, button) -> {
                Application.ActivityLifecycleCallbacks callback = new Application.ActivityLifecycleCallbacks() {
                    boolean paused;
                    public void onActivityPaused(Activity a) { if (a == activity) paused = true; }
                    public void onActivityResumed(Activity a) {
                        if (a != activity || !paused) return;
                        activity.getApplication().unregisterActivityLifecycleCallbacks(this);
                        if (Environment.isExternalStorageManager()) confirmedAction.run();
                        else Toast.makeText(activity, "Move cancelled: file access was not granted", Toast.LENGTH_LONG).show();
                    }
                    public void onActivityDestroyed(Activity a) { if (a == activity) activity.getApplication().unregisterActivityLifecycleCallbacks(this); }
                    public void onActivityCreated(Activity a, Bundle b) {}
                    public void onActivityStarted(Activity a) {}
                    public void onActivityStopped(Activity a) {}
                    public void onActivitySaveInstanceState(Activity a, Bundle b) {}
                };
                activity.getApplication().registerActivityLifecycleCallbacks(callback);
                try { activity.startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + activity.getPackageName()))); }
                catch (Exception e) {
                    activity.getApplication().unregisterActivityLifecycleCallbacks(callback);
                    Toast.makeText(activity, "File access settings unavailable", Toast.LENGTH_LONG).show();
                }
            }).show();
    }
}
