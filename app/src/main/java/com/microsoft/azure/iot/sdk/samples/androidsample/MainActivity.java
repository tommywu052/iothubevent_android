package com.microsoft.azure.iot.sdk.samples.androidsample;

import android.app.TaskStackBuilder;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.microsoft.azure.eventhubs.EventData;
import com.microsoft.azure.eventhubs.EventHubException;
import com.microsoft.azure.eventhubs.EventHubRuntimeInformation;
import com.microsoft.azure.eventhubs.EventPosition;
import com.microsoft.azure.eventhubs.PartitionReceiver;
import com.microsoft.azure.eventhubs.ConnectionStringBuilder;
import com.microsoft.azure.eventhubs.EventHubClient;
import com.microsoft.azure.sdk.iot.device.DeviceClient;
import com.microsoft.azure.sdk.iot.device.DeviceTwin.DeviceMethodData;
import com.microsoft.azure.sdk.iot.device.IotHubClientProtocol;
import com.microsoft.azure.sdk.iot.device.IotHubConnectionStatusChangeCallback;
import com.microsoft.azure.sdk.iot.device.IotHubConnectionStatusChangeReason;
import com.microsoft.azure.sdk.iot.device.IotHubEventCallback;
import com.microsoft.azure.sdk.iot.device.IotHubMessageResult;
import com.microsoft.azure.sdk.iot.device.IotHubStatusCode;
import com.microsoft.azure.sdk.iot.device.Message;
import com.microsoft.azure.sdk.iot.device.transport.IotHubConnectionStatus;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;


public class MainActivity extends AppCompatActivity {

    private final String connString = BuildConfig.DeviceConnectionString;;
    NotificationHandler nHandler;
    private double temperature;
    private double humidity;
    private String msgStr;
    private Message sendMessage;
    private String lastException;

    private DeviceClient client;

    IotHubClientProtocol protocol = IotHubClientProtocol.MQTT;

    Button btnStart;
    Button btnStop;
    Button btnStartReceive; //denisetest
    Button btnStopReceive; //denisetest

    TextView txtMsgsSentVal;
    TextView txtLastTempVal;
    TextView txtMsgsReceived;
    TextView txtLastHumidityVal;
    TextView txtLastMsgSentVal;
    TextView txtLastMsgReceivedVal;

    private int msgSentCount = 0;
    private int receiptsConfirmedCount = 0;
    private int sendFailuresCount = 0;
    private int msgReceivedCount = 0;
    private int sendMessagesInterval = 5000;

    private final Handler handler = new Handler();
    private Thread sendThread;

    private static final int METHOD_SUCCESS = 200;
    public static final int METHOD_THROWS = 403;
    private static final int METHOD_NOT_DEFINED = 404;

    // az iot hub show --query properties.eventHubEndpoints.events.endpoint --name {your IoT Hub name}
    //private static final String eventHubsCompatibleEndpoint = "sb://iothub-ns-denise-iot-843328-0faf5805e3.servicebus.windows.net/";
    private static final String eventHubsCompatibleEndpoint = "sb://ihsuprodbyres104dednamespace.servicebus.windows.net/";

    // az iot hub show --query properties.eventHubEndpoints.events.path --name {your IoT Hub name}
    private static final String eventHubsCompatiblePath = "iothub-ehub-a9iothub-1026438-5f846a7cb3";

    // az iot hub policy show --name service --query primaryKey --hub-name {your IoT Hub name}
    private static final String iotHubSasKey = "lS8pfiUPUJTcBbTMd6c7+V/qoydNrsepw7gql5E64JM=";
    private static final String iotHubSasKeyName = "iothubowner";

    // Track all the PartitionReciever instances created.
    private static ArrayList<PartitionReceiver> receivers = new ArrayList<PartitionReceiver>();

    private static EventHubClient ehClient;
    private static ExecutorService executorService;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        nHandler = NotificationHandler.getInstance(this);
        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);
        btnStartReceive = findViewById(R.id.btnStartReceive);//denisetest
        btnStopReceive = findViewById(R.id.btnStopReceive);//denisetest

        txtMsgsSentVal = findViewById(R.id.txtMsgsSentVal);

        txtLastTempVal = findViewById(R.id.txtLastTempVal);
        txtLastHumidityVal = findViewById(R.id.txtLastHumidityVal);
        txtLastMsgSentVal = findViewById(R.id.txtLastMsgSentVal);
        txtLastMsgReceivedVal = findViewById(R.id.txtLastMsgReceivedVal);

        btnStop.setEnabled(false);
        btnStopReceive.setEnabled(false);
    }

    private void stop()
    {
        new Thread(new Runnable() {
            public void run()
            {
                try
                {
                    sendThread.interrupt();
                    client.closeNow();
                    System.out.println("Shutting down...");
                }
                catch (Exception e)
                {
                    lastException = "Exception while closing IoTHub connection: " + e;
                    handler.post(exceptionRunnable);
                }
            }
        }).start();
    }

    public void btnStopOnClick(View v)
    {
        stop();

        btnStart.setEnabled(true);
        btnStop.setEnabled(false);
    }

    public void btnStopReceiveOnClick(View v)
    {
        stopReceiving();

        btnStartReceive.setEnabled(true);
        btnStopReceive.setEnabled(false);
    }

    private void stopReceiving() {
        System.out.println("Shutting down...");

        try {
            for (PartitionReceiver receiver : receivers) {
                receiver.closeSync();
            }
            ehClient.closeSync();
        } catch (EventHubException e) {
            e.printStackTrace();
            System.out.println("stopReceiving()...Error receive data");
        }
        executorService.shutdown();
//        System.exit(0);
    }

    private void start()
    {
        sendThread = new Thread(new Runnable() {
            public void run()
            {
                try
                {
                    initClient();
                    for(;;)
                    {
                        sendMessages();
                        Thread.sleep(sendMessagesInterval);
                    }
                }
                catch (InterruptedException e)
                {
                    return;
                }
                catch (Exception e)
                {
                    lastException = "Exception while opening IoTHub connection: " + e;
                    handler.post(exceptionRunnable);
                }
            }
        });

        sendThread.start();
    }

    public void btnStartOnClick(View v)
    {
        start();

        btnStart.setEnabled(false);
        btnStop.setEnabled(true);
    }

    public void btnStartReceiveOnClick(View v) {
        //start();
        final ConnectionStringBuilder connStr;
        try {
            connStr = new ConnectionStringBuilder()
                    .setEndpoint(new URI(eventHubsCompatibleEndpoint))
                    .setEventHubName(eventHubsCompatiblePath)
                    .setSasKeyName(iotHubSasKeyName)
                    .setSasKey(iotHubSasKey);

            // Create an EventHubClient instance to connect to the
            // IoT Hub Event Hubs-compatible endpoint.
            executorService = Executors.newSingleThreadExecutor();
            ehClient = EventHubClient.createSync(connStr.toString(), executorService);

            // Use the EventHubRunTimeInformation to find out how many partitions
            // there are on the hub.
            final EventHubRuntimeInformation eventHubInfo = ehClient.getRuntimeInformation().get();

            // Create a PartitionReciever for each partition on the hub.
            for (String partitionId : eventHubInfo.getPartitionIds()) {
                receiveMessages(ehClient, partitionId);

            }

        } catch (URISyntaxException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (EventHubException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        //showNotification("Demooo");
        //nHandler.createSimpleNotification(this);
        btnStartReceive.setEnabled(false);
        btnStopReceive.setEnabled(true);
    }

    // Asynchronously create a PartitionReceiver for a partition and then start
    // reading any messages sent from the simulated client.
    private void receiveMessages(EventHubClient ehClient, String partitionId)
            throws EventHubException, ExecutionException, InterruptedException {

        final ExecutorService executorService = Executors.newSingleThreadExecutor();

        // Create the receiver using the default consumer group.
        // For the purposes of this sample, read only messages sent since
        // the time the receiver is created. Typically, you don't want to skip any messages.
        ehClient.createReceiver("android", partitionId,
                EventPosition.fromEnqueuedTime(Instant.now())).thenAcceptAsync(receiver -> {
            System.out.println(String.format("Starting receive loop on partition: %s", partitionId));
            System.out.println(String.format("Reading messages sent since: %s", Instant.now().toString()));

            receivers.add(receiver);

            while (true) {
                try {
                    // Check for EventData - this methods times out if there is nothing to retrieve.
                    Iterable<EventData> receivedEvents = receiver.receiveSync(100);

                    // If there is data in the batch, process it.
                    if (receivedEvents != null) {
                        for (EventData receivedEvent : receivedEvents) {
                            String payload = new String(receivedEvent.getBytes(), Charset.defaultCharset());
                            System.out.println(String.format("Telemetry received:\n %s",
                                    payload));
                            nHandler.createSimpleNotification(this,payload);
                            System.out.println(String.format("Application properties (set by device):\n%s",receivedEvent.getProperties().toString()));
                            System.out.println(String.format("System properties (set by IoT Hub):\n%s\n",receivedEvent.getSystemProperties().toString()));
                            msgReceivedCount++;
                            TextView txtMsgsReceivedVal = findViewById(R.id.txtMsgsReceivedVal);
                            txtMsgsReceivedVal.setText(Integer.toString(msgReceivedCount));
                            txtLastMsgReceivedVal.setText("[" + payload+ "]");

                        }
                    }
                } catch (EventHubException e) {
                    System.out.println("Error reading EventData");
                }
            }
        }, executorService);
    }

    // Display the topbar notification
    private void showNotification(String text) {
        NotificationCompat.Builder mBuilder =   new NotificationCompat.Builder(this)
                .setSmallIcon(com.microsoft.azure.iot.sdk.samples.androidsample.R.drawable.icon) // notification icon
                .setContentTitle("Notification!") // title for notification
                .setContentText(text) // message for notification
                .setAutoCancel(true); // clear notification after click
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this,0,intent,0);
        mBuilder.setContentIntent(pi);
        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(0, mBuilder.build());
    }

    private void dispNotification(String txt) {
        NotificationManager mNotificationManager = (NotificationManager)
                getSystemService(Context.NOTIFICATION_SERVICE);
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                intent, PendingIntent.FLAG_ONE_SHOT);

        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.icon)
                        .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.icon))
                        .setContentTitle("Event Notification")
                        .setDefaults(Notification.DEFAULT_SOUND)
                        .setStyle(new NotificationCompat.BigTextStyle()
                                .bigText(txt))
                        .setContentText(txt)
                        .setPriority(Notification.PRIORITY_MAX);

        mBuilder.setContentIntent(contentIntent);
        mNotificationManager.notify((int)0, mBuilder.build());
    }

    private void sendNotification(String txt) {
        Context context = getApplicationContext();
        CharSequence text = txt;
        int duration = Toast.LENGTH_SHORT;

        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }

    final Runnable updateRunnable = new Runnable() {
        public void run() {
            txtLastTempVal.setText(String.format("%.2f",temperature));
            txtLastHumidityVal.setText(String.format("%.2f",humidity));
            txtMsgsSentVal.setText(Integer.toString(msgSentCount));
            txtLastMsgSentVal.setText("[" + new String(sendMessage.getBytes(), Message.DEFAULT_IOTHUB_MESSAGE_CHARSET) + "]");
        }
    };

    final Runnable exceptionRunnable = new Runnable() {
        public void run() {
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setMessage(lastException);
            builder.show();
            System.out.println(lastException);
            btnStart.setEnabled(true);
            btnStop.setEnabled(false);
        }
    };

    final Runnable methodNotificationRunnable = new Runnable() {
        public void run() {
            Context context = getApplicationContext();
            CharSequence text = "Set Send Messages Interval to " + sendMessagesInterval + "ms";
            int duration = Toast.LENGTH_LONG;

            Toast toast = Toast.makeText(context, text, duration);
            toast.show();
        }
    };

    private void sendMessages()
    {
        temperature = 20.0 + Math.random() * 10;
        humidity = 30.0 + Math.random() * 20;
        msgStr = "\"temperature\":" + String.format("%.2f",temperature) + ", \"humidity\":" + String.format("%.2f",humidity);
        try
        {
            sendMessage = new Message(msgStr);
            sendMessage.setProperty("temperatureAlert", temperature > 28 ? "true" : "false");
            sendMessage.setMessageId(java.util.UUID.randomUUID().toString());
            System.out.println("Message Sent: " + msgStr);
            EventCallback eventCallback = new EventCallback();
            client.sendEventAsync(sendMessage, eventCallback, msgSentCount);
            msgSentCount++;
            handler.post(updateRunnable);
        }
        catch (Exception e)
        {
            System.err.println("Exception while sending event: " + e);
        }
    }

    private void initClient() throws URISyntaxException, IOException
    {
        client = new DeviceClient(connString, protocol);

        try
        {
            client.registerConnectionStatusChangeCallback(new IotHubConnectionStatusChangeCallbackLogger(), new Object());
            client.open();
            MessageCallback callback = new MessageCallback();
            client.setMessageCallback(callback, null);
            client.subscribeToDeviceMethod(new SampleDeviceMethodCallback(), getApplicationContext(), new DeviceMethodStatusCallBack(), null);
        }
        catch (Exception e)
        {
            System.err.println("Exception while opening IoTHub connection: " + e);
            client.closeNow();
            System.out.println("Shutting down...");
        }
    }

    class EventCallback implements IotHubEventCallback
    {
        public void execute(IotHubStatusCode status, Object context)
        {
            Integer i = context instanceof Integer ? (Integer) context : 0;
            System.out.println("IoT Hub responded to message " + i.toString()
                    + " with status " + status.name());

            if((status == IotHubStatusCode.OK) || (status == IotHubStatusCode.OK_EMPTY))
            {
                TextView txtReceiptsConfirmedVal = findViewById(R.id.txtReceiptsConfirmedVal);
                receiptsConfirmedCount++;
                txtReceiptsConfirmedVal.setText(Integer.toString(receiptsConfirmedCount));
            }
            else
            {
                TextView txtSendFailuresVal = findViewById(R.id.txtSendFailuresVal);
                sendFailuresCount++;
                txtSendFailuresVal.setText(Integer.toString(sendFailuresCount));
            }
        }
    }

    class MessageCallback implements com.microsoft.azure.sdk.iot.device.MessageCallback
    {
        public IotHubMessageResult execute(Message msg, Object context)
        {
            System.out.println(
                    "Received message with content: " + new String(msg.getBytes(), Message.DEFAULT_IOTHUB_MESSAGE_CHARSET));
            //msgReceivedCount++;
            //TextView txtMsgsReceivedVal = findViewById(R.id.txtMsgsReceivedVal);
            //txtMsgsReceivedVal.setText(Integer.toString(msgReceivedCount));
            //txtLastMsgReceivedVal.setText("[" + new String(msg.getBytes(), Message.DEFAULT_IOTHUB_MESSAGE_CHARSET) + "]");
            return IotHubMessageResult.COMPLETE;
        }
    }

    protected static class IotHubConnectionStatusChangeCallbackLogger implements IotHubConnectionStatusChangeCallback
    {
        @Override
        public void execute(IotHubConnectionStatus status, IotHubConnectionStatusChangeReason statusChangeReason, Throwable throwable, Object callbackContext)
        {
            System.out.println();
            System.out.println("CONNECTION STATUS UPDATE: " + status);
            System.out.println("CONNECTION STATUS REASON: " + statusChangeReason);
            System.out.println("CONNECTION STATUS THROWABLE: " + (throwable == null ? "null" : throwable.getMessage()));
            System.out.println();

            if (throwable != null)
            {
                throwable.printStackTrace();
            }

            if (status == IotHubConnectionStatus.DISCONNECTED)
            {
                //connection was lost, and is not being re-established. Look at provided exception for
                // how to resolve this issue. Cannot send messages until this issue is resolved, and you manually
                // re-open the device client
            }
            else if (status == IotHubConnectionStatus.DISCONNECTED_RETRYING)
            {
                //connection was lost, but is being re-established. Can still send messages, but they won't
                // be sent until the connection is re-established
            }
            else if (status == IotHubConnectionStatus.CONNECTED)
            {
                //Connection was successfully re-established. Can send messages.
            }
        }
    }

    private int method_setSendMessagesInterval(Object methodData) throws UnsupportedEncodingException, JSONException
    {
        String payload = new String((byte[])methodData, "UTF-8").replace("\"", "");
        JSONObject obj = new JSONObject(payload);
        sendMessagesInterval = obj.getInt("sendInterval");
        handler.post(methodNotificationRunnable);
        return METHOD_SUCCESS;
}

    private int method_default(Object data)
    {
        System.out.println("invoking default method for this device");
        // Insert device specific code here
        return METHOD_NOT_DEFINED;
    }

    protected class DeviceMethodStatusCallBack implements IotHubEventCallback
    {
        public void execute(IotHubStatusCode status, Object context)
        {
            System.out.println("IoT Hub responded to device method operation with status " + status.name());
        }
    }

    protected class SampleDeviceMethodCallback implements com.microsoft.azure.sdk.iot.device.DeviceTwin.DeviceMethodCallback
    {
        @Override
        public DeviceMethodData call(String methodName, Object methodData, Object context)
        {
            DeviceMethodData deviceMethodData ;
            try {
                switch (methodName) {
                    case "setSendMessagesInterval": {
                        int status = method_setSendMessagesInterval(methodData);
                        deviceMethodData = new DeviceMethodData(status, "executed " + methodName);
                        break;
                    }
                    default: {
                        int status = method_default(methodData);
                        deviceMethodData = new DeviceMethodData(status, "executed " + methodName);
                    }
                }
            }
            catch (Exception e)
            {
                int status = METHOD_THROWS;
                deviceMethodData = new DeviceMethodData(status, "Method Throws " + methodName);
            }
            return deviceMethodData;
        }
    }
}
