package org.servalproject;

import java.io.IOException;
import java.util.Date;

import org.servalproject.batman.RoutingParser;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;
import android.widget.RemoteViews;

public class StatusNotification {
	TrafficCounter trafficCounterThread;
	private ServalBatPhoneApplication app;

    // Notification
	private NotificationManager notificationManager;

	public StatusNotification(ServalBatPhoneApplication app) {
		this.app=app;
        // init notificationManager
        this.notificationManager = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
	}

	private String formatCount(long count, boolean rate) {
		// Converts the supplied argument into a string.
		// 'rate' indicates whether is a total bytes, or bits per sec.
		// Under 2Mb, returns "xxx.xKb"
		// Over 2Mb, returns "xxx.xxMb"
		if (count < 1e6 * 2)
			return ((float)((int)(count*10/1024))/10 + (rate ? "kbps" : "kB"));
		return ((float)((int)(count*100/1024/1024))/100 + (rate ? "mbps" : "MB"));
	}

    // Notification
    public void showStatusNotification() {
		if (this.trafficCounterThread == null || this.trafficCounterThread.isAlive() == false) {
			this.trafficCounterThread = new TrafficCounter();
			this.trafficCounterThread.start();
		}
    }

    public void hideStatusNotification(){
    	if (this.trafficCounterThread != null)
    		this.trafficCounterThread.interrupt();
    }

   	class TrafficCounter extends Thread {
   		// Note that sending too frequent updates seems to clog the phone UI.
   		long previousDownload;
   		long previousUpload;
   		long lastTimeChecked;

   		@Override
		public void run() {
            try {
	   			this.previousDownload = this.previousUpload = 0;
	   			this.lastTimeChecked = new Date().getTime();
				RoutingParser parser = new RoutingParser();
				PowerManager pm = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
	   			String adhocNetworkDevice = app.getAdhocNetworkDevice();

	   			int updateCounter=0;
	   			int lastPeerCount=-1;
	   			int peerCount;

	   			while (true) {
					if (pm.isScreenOn()){
				        try {
							peerCount = parser.getPeerList().size() + 1;
						} catch (IOException e) {
							peerCount=1;
							Log.v("BatPhone",e.toString(),e);
						}

						// TODO, when the screen is locked, only update when the peer count changes.
						if (peerCount!=lastPeerCount)
							Instrumentation.valueChanged(Instrumentation.Variable.PeerCount, peerCount);

						if (peerCount!=lastPeerCount || updateCounter-- <=0){
							// only update the notification if the peer count has changed, or at least every 10 seconds
							lastPeerCount=peerCount;
							updateCounter=5;

					        // Check data count
					        long [] trafficCount = app.coretask.getDataTraffic(adhocNetworkDevice);
					        long currentTime = new Date().getTime();
					        float elapsedTime = ((currentTime - this.lastTimeChecked) / 1000);
					        this.lastTimeChecked = currentTime;
					        long upRate=(long)((trafficCount[0] - this.previousUpload)*8/elapsedTime);
					        long downRate=(long)((trafficCount[1] - this.previousDownload)*8/elapsedTime);

							RemoteViews contentView = new RemoteViews(
									app.getPackageName(), R.layout.notification);
							contentView.setImageViewResource(
									R.id.notificationImage,
									R.drawable.start_notification);

							Notification notification = new Notification(
									R.drawable.start_notification,
									"Serval BatPhone",
									System.currentTimeMillis());
							notification.contentView = contentView;
							notification.flags = Notification.FLAG_ONGOING_EVENT;
							notification.contentIntent = PendingIntent
									.getActivity(app, 0, new Intent(app,
											MainActivity.class),
											PendingIntent.FLAG_UPDATE_CURRENT);
							notification.number=peerCount;
					    	notification.contentView.setTextViewText(R.id.peerCount, Integer.toString(peerCount));
					    	notification.contentView.setTextViewText(R.id.trafficUp, formatCount(trafficCount[0], false));
					    	notification.contentView.setTextViewText(R.id.trafficDown, formatCount(trafficCount[1], false));
					    	notification.contentView.setTextViewText(R.id.trafficUpRate, formatCount(upRate, true));
					    	notification.contentView.setTextViewText(R.id.trafficDownRate, formatCount(downRate, true));
					    	notificationManager.notify(-1, notification);
						}
					}
					Thread.sleep(1000);
	   			}
            } catch (InterruptedException e) {
            }
        	notificationManager.cancel(-1);
   		}
   	}
}
