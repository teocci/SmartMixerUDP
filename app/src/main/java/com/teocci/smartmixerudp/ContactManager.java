package com.teocci.smartmixerudp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.HashMap;

import android.util.Log;

import com.teocci.smartmixerudp.listeners.ContactUpdateReceiver;
import com.teocci.smartmixerudp.utils.LogHelper;

public class ContactManager
{
    private static final String TAG = LogHelper.makeLogTag(ContactManager.class);
    public static final int BROADCAST_PORT = 50001; // Socket on which packets are sent/received
    private static final int BROADCAST_INTERVAL = 5000; // Milliseconds
    private static final int BROADCAST_BUF_SIZE = 1024;
    private boolean BROADCAST = true;
    private boolean LISTEN = true;
    private HashMap<String, InetAddress> contacts;
    private InetAddress broadcastIP;
    private String deviceAddress;
    private String deviceName;
    private ContactUpdateReceiver contactUpdateReceiver;

    public ContactManager(String name, String address, InetAddress broadcastIP)
    {
        this.contacts = new HashMap<>();
        this.contactUpdateReceiver = null;
        this.deviceName = name;
        this.deviceAddress = address;
        this.broadcastIP = broadcastIP;
        listen();
        broadcastName(name, broadcastIP);
    }

    public HashMap<String, InetAddress> getContacts()
    {
        return contacts;
    }

    public void addContact(String name, InetAddress address)
    {
        Log.e(TAG, "deviceName: " + deviceName + " deviceAddress:" + deviceAddress);
        Log.e(TAG, "name: " + name + " address:" + address);
        if (deviceName.equals(name) && deviceAddress.equals(address.getHostAddress())) {
            return;
        }
        // If the contact is not already known to us, add it
        if (!contacts.containsKey(name)) {
            Log.i(TAG, "Adding contact: " + name);
            contacts.put(name, address);
            Log.i(TAG, "#Contacts: " + contacts.size());
            if (contactUpdateReceiver != null){
                contactUpdateReceiver.onContactUpdate();
            }
            return;
        }
        Log.i(TAG, "Contact already exists: " + name);
    }

    public void removeContact(String name)
    {
        // If the contact is known to us, remove it
        if (contacts.containsKey(name)) {
            Log.i(TAG, "Removing contact: " + name);
            contacts.remove(name);
            Log.i(TAG, "#Contacts: " + contacts.size());
            if (contactUpdateReceiver != null){
                contactUpdateReceiver.onContactUpdate();
            }
            return;
        }
        Log.i(TAG, "Cannot remove contact. " + name + " does not exist.");
    }

    public void bye(final String name)
    {
        // Sends a Bye notification to other devices
        Thread byeThread = new Thread(new Runnable()
        {

            @Override
            public void run()
            {
                try {
                    Log.i(TAG, "Attempting to broadcast BYE notification!");
                    String notification = "BYE:" + name;
                    byte[] message = notification.getBytes();
                    DatagramSocket socket = new DatagramSocket();
                    socket.setBroadcast(true);
                    DatagramPacket packet = new DatagramPacket(message, message.length, broadcastIP, BROADCAST_PORT);
                    socket.send(packet);
                    Log.i(TAG, "Broadcast BYE notification!");
                    socket.disconnect();
                    socket.close();
                    return;
                } catch (SocketException e) {
                    Log.e(TAG, "SocketException during BYE notification: " + e);
                } catch (IOException e) {
                    Log.e(TAG, "IOException during BYE notification: " + e);
                }
            }
        });
        byeThread.start();
    }

    public void broadcastName(final String name, final InetAddress broadcastIP)
    {
        // Broadcasts the name of the device at a regular interval
        Log.i(TAG, "[broadcastName]Broadcasting started!");
        Thread broadcastThread = new Thread(new Runnable()
        {

            @Override
            public void run()
            {
                try {
                    String request = "ADD:" + name;
                    byte[] message = request.getBytes();
                    DatagramSocket socket = new DatagramSocket();
                    socket.setBroadcast(true);
                    DatagramPacket packet = new DatagramPacket(message, message.length, broadcastIP, BROADCAST_PORT);
                    while (BROADCAST) {
                        socket.send(packet);
                        Log.e(TAG, "[broadcastName]Broadcast packet sent: " + packet.getAddress().toString());
                        Thread.sleep(BROADCAST_INTERVAL);
                    }
                    Log.i(TAG, "[broadcastName]Broadcaster ending!");
                    socket.disconnect();
                    socket.close();
                    return;
                } catch (SocketException e) {
                    Log.e(TAG, "[broadcastName]SocketException in broadcast: " + e);
                    Log.i(TAG, "[broadcastName]Broadcaster ending!");
                    return;
                } catch (IOException e) {
                    Log.e(TAG, "[broadcastName]IOException in broadcast: " + e);
                    Log.i(TAG, "[broadcastName]Broadcaster ending!");
                    return;
                } catch (InterruptedException e) {
                    Log.e(TAG, "[broadcastName]InterruptedException in broadcast: " + e);
                    Log.i(TAG, "[broadcastName]Broadcaster ending!");
                    return;
                }
            }
        });
        broadcastThread.start();
    }

    public void stopBroadcasting()
    {
        // Ends the broadcasting thread
        BROADCAST = false;
    }

    public void listen()
    {
        // Create the listener thread
        Log.i(TAG, "[listen]Listening started!");
        Thread listenThread = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                DatagramSocket socket;
                try {
                    socket = new DatagramSocket(BROADCAST_PORT);
                } catch (SocketException e) {
                    Log.e(TAG, "[listen]SocketException in listener: " + e);
                    return;
                }
                byte[] buffer = new byte[BROADCAST_BUF_SIZE];

                while (LISTEN) {
                    listen(socket, buffer);
                }
                Log.i(TAG, "[listen]Listener ending!");
                socket.disconnect();
                socket.close();
                return;
            }

            public void listen(DatagramSocket socket, byte[] buffer)
            {
                try {
                    //Listen in for new notifications
                    Log.i(TAG, "[listen]Listening for a packet!");
                    DatagramPacket packet = new DatagramPacket(buffer, BROADCAST_BUF_SIZE);
                    socket.setSoTimeout(15000);
                    socket.receive(packet);
                    String data = new String(buffer, 0, packet.getLength());
                    Log.e(TAG, "[listen]Packet received: " + data);
                    String action = data.substring(0, 4);
                    if (action.equals("ADD:")) {
                        // Add notification received. Attempt to add contact
                        Log.e(TAG, "[listen]Listener received ADD request");
                        addContact(data.substring(4, data.length()), packet.getAddress());
                    } else if (action.equals("BYE:")) {
                        // Bye notification received. Attempt to remove contact
                        Log.i(TAG, "[listen]Listener received BYE request");
                        removeContact(data.substring(4, data.length()));
                    } else {
                        // Invalid notification received
                        Log.w(TAG, "[listen]Listener received invalid request: " + action);
                        return;
                    }
                } catch (SocketTimeoutException e) {
                    Log.i(TAG, "[listen]No packet received!");
                    if (LISTEN) {
                        listen(socket, buffer);
                    }
                    return;
                } catch (SocketException e) {
                    Log.e(TAG, "[listen]SocketException in listen: " + e);
                    Log.i(TAG, "[listen]Listener ending!");
                    return;
                } catch (IOException e) {
                    Log.e(TAG, "[listen]IOException in listen: " + e);
                    Log.i(TAG, "[listen]Listener ending!");
                    return;
                }
            }
        });
        listenThread.start();
    }

    public void stopListening()
    {
        // Stops the listener thread
        LISTEN = false;
    }

    public void setContactUpdateReceiver(ContactUpdateReceiver contactUpdateReceiver)
    {
        this.contactUpdateReceiver = contactUpdateReceiver;
    }

    public void removeContactUpdateReceiver()
    {
        this.contactUpdateReceiver = null;
    }

    public ContactUpdateReceiver getContactUpdateReceiver()
    {
        return contactUpdateReceiver;
    }
}
