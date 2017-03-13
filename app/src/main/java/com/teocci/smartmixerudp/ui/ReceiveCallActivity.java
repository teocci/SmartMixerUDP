package com.teocci.smartmixerudp.ui;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import android.app.Activity;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import com.teocci.smartmixerudp.AudioCall;
import com.teocci.smartmixerudp.R;
import com.teocci.smartmixerudp.utils.LogHelper;

public class ReceiveCallActivity extends Activity
{
    private static final String TAG = LogHelper.makeLogTag(ReceiveCallActivity.class);
    private static final int BROADCAST_PORT = 50002;
    private static final int BUF_SIZE = 1024;
    private String contactIp;
    private String contactName;
    private boolean LISTEN = true;
    private boolean IN_CALL = false;
    private AudioCall call;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receive_call);

        Intent intent = getIntent();
        contactName = intent.getStringExtra(MainActivity.EXTRA_CONTACT);
        contactIp = intent.getStringExtra(MainActivity.EXTRA_IP);

        TextView textView = (TextView) findViewById(R.id.textViewIncomingCall);
        textView.setText(getResources().getString(R.string.incoming_call, contactName));

        final Button endButton = (Button) findViewById(R.id.buttonEndCall1);
        endButton.setVisibility(View.INVISIBLE);

        startListener();

        // ACCEPT BUTTON
        Button acceptButton = (Button) findViewById(R.id.buttonAccept);
        acceptButton.setOnClickListener(new OnClickListener()
        {

            @Override
            public void onClick(View v)
            {
                try {
                    // Accepting call. Send a notification and start the call
                    sendMessage("ACC:");
                    InetAddress address = InetAddress.getByName(contactIp);
                    Log.i(TAG, "Calling " + address.toString());

                    call = new AudioCall(address);
                    call.startCall();
                    IN_CALL = true;

                    // Hide the buttons as they're not longer required
                    Button accept = (Button) findViewById(R.id.buttonAccept);
                    accept.setEnabled(false);

                    Button reject = (Button) findViewById(R.id.buttonReject);
                    reject.setEnabled(false);

                    endButton.setVisibility(View.VISIBLE);
                } catch (UnknownHostException e) {
                    Log.e(TAG, "UnknownHostException in acceptButton: " + e);
                } catch (Exception e) {
                    Log.e(TAG, "Exception in acceptButton: " + e);
                }
            }
        });

        // REJECT BUTTON
        Button rejectButton = (Button) findViewById(R.id.buttonReject);
        rejectButton.setOnClickListener(new OnClickListener()
        {

            @Override
            public void onClick(View v)
            {
                // Send a reject notification and end the call
                sendMessage("REJ:");
                endCall();
            }
        });

        // END BUTTON
        endButton.setOnClickListener(new OnClickListener()
        {

            @Override
            public void onClick(View v)
            {
                endCall();
            }
        });
    }

    private void endCall()
    {
        // End the call and send a notification
        stopListener();
        if (IN_CALL) {
            call.endCall();
        }
        sendMessage("END:");
        finish();
    }

    private void startListener()
    {
        // Creates the listener thread
        LISTEN = true;
        Thread listenThread = new Thread(new Runnable()
        {

            @Override
            public void run()
            {
                try {
                    Log.i(TAG, "[startListener]Listener started!");
                    DatagramSocket socket = new DatagramSocket(BROADCAST_PORT);
                    socket.setSoTimeout(1500);
                    byte[] buffer = new byte[BUF_SIZE];
                    DatagramPacket packet = new DatagramPacket(buffer, BUF_SIZE);
                    while (LISTEN) {
                        try {
                            Log.i(TAG, "[startListener]Listening for packets");
                            socket.receive(packet);
                            String data = new String(buffer, 0, packet.getLength());
                            Log.i(TAG, "[startListener]Packet received from " + packet.getAddress() + " with contents: " + data);
                            String action = data.substring(0, 4);
                            if (action.equals("END:")) {
                                // End call notification received. End call
                                endCall();
                            } else {
                                // Invalid notification received.
                                Log.w(TAG, "[startListener]" + packet.getAddress() + " sent invalid message: " + data);
                            }
                        } catch (IOException e) {
                            Log.e(TAG, "[startListener]IOException in Listener " + e);
                        }
                    }
                    Log.i(TAG, "[startListener]Listener ending");
                    socket.disconnect();
                    socket.close();
                    return;
                } catch (SocketException e) {
                    Log.e(TAG, "[startListener]SocketException in Listener " + e);
                    endCall();
                }
            }
        });
        listenThread.start();
    }

    private void stopListener()
    {
        // Ends the listener thread
        LISTEN = false;
    }

    private void sendMessage(final String message)
    {
        // Creates a thread for sending notifications
        Thread replyThread = new Thread(new Runnable()
        {

            @Override
            public void run()
            {
                try {
                    InetAddress address = InetAddress.getByName(contactIp);
                    byte[] data = message.getBytes();
                    DatagramSocket socket = new DatagramSocket();
                    DatagramPacket packet = new DatagramPacket(data, data.length, address, BROADCAST_PORT);
                    socket.send(packet);
                    Log.i(TAG, "Sent message( " + message + " ) to " + contactIp);
                    socket.disconnect();
                    socket.close();
                } catch (UnknownHostException e) {
                    Log.e(TAG, "Failure. UnknownHostException in sendMessage: " + contactIp);
                } catch (SocketException e) {
                    Log.e(TAG, "Failure. SocketException in sendMessage: " + e);
                } catch (IOException e) {
                    Log.e(TAG, "Failure. IOException in sendMessage: " + e);
                }
            }
        });
        replyThread.start();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.receive_call, menu);
        return true;
    }
}
