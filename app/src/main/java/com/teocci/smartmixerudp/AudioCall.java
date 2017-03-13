package com.teocci.smartmixerudp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.util.Log;

import com.teocci.smartmixerudp.utils.LogHelper;

public class AudioCall
{
    private static final String TAG  = LogHelper.makeLogTag(AudioCall.class);
    private static final int SAMPLE_RATE = 8000; // Hertz
    private static final int SAMPLE_INTERVAL = 20; // Milliseconds
    private static final int SAMPLE_SIZE = 2; // Bytes
    private static final int FRAME_SIZE = SAMPLE_INTERVAL * SAMPLE_INTERVAL * SAMPLE_SIZE * 2; //Bytes
    private InetAddress address; // Address to call
    private int port = 50000; // Port the packets are addressed to
    private boolean mic = false; // Enable mic?
    private boolean speakers = false; // Enable speakers?

    public AudioCall(InetAddress address)
    {
        this.address = address;
    }

    public void startCall()
    {
        startMic();
        startSpeakers();
    }

    public void endCall()
    {
        Log.i(TAG, "Ending call!");
        muteMic();
        muteSpeakers();
    }

    public void muteMic()
    {
        mic = false;
    }

    public void muteSpeakers()
    {
        speakers = false;
    }

    public void startMic()
    {
        // Creates the thread for capturing and transmitting audio
        mic = true;
        Thread thread = new Thread(new Runnable()
        {

            @Override
            public void run()
            {
                // Create an instance of the AudioRecord class
                Log.i(TAG, "[startMic]Send thread started. Thread id: " + Thread.currentThread().getId());
                AudioRecord audioRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                        AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) * 10);
                int bytes_read;
                int bytes_sent = 0;
                byte[] buf = new byte[FRAME_SIZE];
                try {
                    // Create a socket and start recording
                    Log.i(TAG, "[startMic]Packet destination: " + address.toString());
                    DatagramSocket socket = new DatagramSocket();
                    audioRecorder.startRecording();
                    while (mic) {
                        // Capture audio from the mic and transmit it
                        bytes_read = audioRecorder.read(buf, 0, FRAME_SIZE);
                        DatagramPacket packet = new DatagramPacket(buf, bytes_read, address, port);
                        socket.send(packet);
                        bytes_sent += bytes_read;
                        Log.i(TAG, "[startMic]Total bytes sent: " + bytes_sent);
                        Thread.sleep(SAMPLE_INTERVAL, 0);
                    }
                    // Stop recording and release resources
                    audioRecorder.stop();
                    audioRecorder.release();
                    socket.disconnect();
                    socket.close();
                    mic = false;
                    return;
                } catch (InterruptedException e) {
                    Log.e(TAG, "[startMic]InterruptedException: " + e.toString());
                    mic = false;
                } catch (SocketException e) {
                    Log.e(TAG, "[startMic]SocketException: " + e.toString());
                    mic = false;
                } catch (UnknownHostException e) {
                    Log.e(TAG, "[startMic]UnknownHostException: " + e.toString());
                    mic = false;
                } catch (IOException e) {
                    Log.e(TAG, "[startMic]IOException: " + e.toString());
                    mic = false;
                }
            }
        });
        thread.start();
    }

    public void startSpeakers()
    {
        // Creates the thread for receiving and playing back audio
        if (!speakers) {

            speakers = true;
            Thread receiveThread = new Thread(new Runnable()
            {

                @Override
                public void run()
                {
                    // Create an instance of AudioTrack, used for playing back audio
                    Log.i(TAG, "[startSpeakers]Receive thread started. Thread id: " + Thread.currentThread().getId());
                    AudioTrack track = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT, FRAME_SIZE, AudioTrack.MODE_STREAM);
                    track.play();
                    try {
                        // Define a socket to receive the audio
                        DatagramSocket socket = new DatagramSocket(port);
                        byte[] buf = new byte[FRAME_SIZE];
                        while (speakers) {
                            // Play back the audio received from packets
                            DatagramPacket packet = new DatagramPacket(buf, FRAME_SIZE);
                            socket.receive(packet);
                            Log.i(TAG, "[startSpeakers]Packet received: " + packet.getLength());
                            track.write(packet.getData(), 0, FRAME_SIZE);
                        }
                        // Stop playing back and release resources
                        socket.disconnect();
                        socket.close();
                        track.stop();
                        track.flush();
                        track.release();
                        speakers = false;
                        return;
                    } catch (SocketException e) {
                        Log.e(TAG, "[startSpeakers]SocketException: " + e.toString());
                        speakers = false;
                    } catch (IOException e) {
                        Log.e(TAG, "[startSpeakers]IOException: " + e.toString());
                        speakers = false;
                    }
                }
            });
            receiveThread.start();
        }
    }
}
