package com.microsoft.azure.iot.sdk.samples.androidsample;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.widget.Toast;

import java.util.Random;

import static android.graphics.Color.RED;
import static android.support.v4.content.ContextCompat.getSystemService;

public class NotificationHandler {
	// Notification handler singleton
	private static NotificationHandler nHandler;
	private static NotificationManager mNotificationManager;


	private NotificationHandler () {}


	/**
	 * Singleton pattern implementation
	 * @return
	 */
	public static  NotificationHandler getInstance(Context context) {
		if(nHandler == null) {
			nHandler = new NotificationHandler();
			mNotificationManager =
					(NotificationManager) context.getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
		}

		return nHandler;
	}


	/**
	 * Shows a simple notification
	 * @param context aplication context
	 */
	public void createSimpleNotification(Context context,String txt) {


// The id of the channel.
		String id = "my_channel_01";

// The user-visible name of the channel.
		CharSequence name = "my_channel_01";

// The user-visible description of the channel.
		String description = "This is  my channel : my_channel_01";

		int importance = mNotificationManager.IMPORTANCE_LOW;


		NotificationChannel mChannel = new NotificationChannel(id, name,importance);

// Configure the notification channel.
		mChannel.setDescription(description);

		mChannel.enableLights(true);
// Sets the notification light color for notifications posted to this
// channel, if the device supports this feature.
		mChannel.setLightColor(RED);

		mChannel.enableVibration(true);
		mChannel.setVibrationPattern(new long[]{100, 200, 300, 400, 500, 400, 300, 200, 400});

		mNotificationManager.createNotificationChannel(mChannel);
		// Creates an explicit intent for an Activity
		Intent resultIntent = new Intent(context, MainActivity.class);

		// Creating a artifical activity stack for the notification activity
		TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
		stackBuilder.addParentStack(MainActivity.class);
		stackBuilder.addNextIntent(resultIntent);

		// Pending intent to the notification manager
		PendingIntent resultPending = stackBuilder
				.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

		// Building the notification
		NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context)
				.setSmallIcon(R.drawable.ic_launcher) // notification icon
				.setContentTitle("Event notification") // main title of the notification
				.setContentText(txt) // notification text
				.setContentIntent(resultPending) // notification intent
				.setOngoing(true)
				.setChannelId(id);

		// mId allows you to update the notification later on.
		mNotificationManager.notify(10, mBuilder.build());
	}


	public void createExpandableNotification (Context context) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
			// Building the expandable content
			NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
			String lorem = context.getResources().getString(R.string.long_lorem);
			String [] content = lorem.split("\\.");

			inboxStyle.setBigContentTitle("This is a big title");
			for (String line : content) {
				inboxStyle.addLine(line);
			}

			// Building the notification
			NotificationCompat.Builder nBuilder = new NotificationCompat.Builder(context)
					.setSmallIcon(R.drawable.ic_launcher) // notification icon
					.setContentTitle("Expandable notification") // title of notification
					.setContentText("This is an example of an expandable notification") // text inside the notification
					.setStyle(inboxStyle); // adds the expandable content to the notification

			mNotificationManager.notify(11, nBuilder.build());

		} else {
			Toast.makeText(context, "Can't show", Toast.LENGTH_LONG).show();
		}
	}


	/**
	 * Show a determinate and undeterminate progress notification
	 * @param context, activity context
	 */
	public void createProgressNotification (final Context context) {

		// used to update the progress notification
		final int progresID = new Random().nextInt(1000);

		// building the notification
		final NotificationCompat.Builder nBuilder = new NotificationCompat.Builder(context)
				.setSmallIcon(R.drawable.icon)
				.setContentTitle("Progres notification")
				.setContentText("Now waiting")
				.setTicker("Progress notification created")
				.setUsesChronometer(true)
				.setProgress(100, 0, true);



		AsyncTask<Integer, Integer, Integer> downloadTask = new AsyncTask<Integer, Integer, Integer>() {
			@Override
			protected void onPreExecute () {
				super.onPreExecute();
				mNotificationManager.notify(progresID, nBuilder.build());
			}

			@Override
			protected Integer doInBackground (Integer... params) {
				try {
					// Sleeps 2 seconds to show the undeterminated progress
					Thread.sleep(5000);

					// update the progress
					for (int i = 0; i < 101; i+=5) {
						nBuilder
							.setContentTitle("Progress running...")
							.setContentText("Now running...")
							.setProgress(100, i, false)
							.setSmallIcon(R.drawable.icon)
							.setContentInfo(i + " %");

						// use the same id for update instead created another one
						mNotificationManager.notify(progresID, nBuilder.build());
						Thread.sleep(500);
					}

				} catch (InterruptedException e) {
					e.printStackTrace();
				}

				return null;
			}


			@Override
			protected void onPostExecute (Integer integer) {
				super.onPostExecute(integer);

				nBuilder.setContentText("Progress finished :D")
						.setContentTitle("Progress finished !!")
						.setTicker("Progress finished !!!")
						.setSmallIcon(R.drawable.icon)
						.setUsesChronometer(false);

				mNotificationManager.notify(progresID, nBuilder.build());
			}
		};

		// Executes the progress task
		downloadTask.execute();
	}


	public void createButtonNotification (Context context) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
			// Prepare intent which is triggered if the  notification button is pressed
			Intent intent = new Intent(context, MainActivity.class);
			PendingIntent pIntent = PendingIntent.getActivity(context, 0, intent, 0);

			// Building the notifcation
			NotificationCompat.Builder nBuilder = new NotificationCompat.Builder(context)
					.setSmallIcon(R.drawable.ic_launcher) // notification icon
					.setContentTitle("Button notification") // notification title
					.setContentText("Expand to show the buttons...") // content text
					.setTicker("Showing button notification") // status bar message
					.addAction(R.drawable.icon, "Accept", pIntent) // accept notification button
					.addAction(R.drawable.icon, "Cancel", pIntent); // cancel notification button

			mNotificationManager.notify(1001, nBuilder.build());

		} else {
			Toast.makeText(context, "You need a higher version", Toast.LENGTH_LONG).show();
		}
	}
}
