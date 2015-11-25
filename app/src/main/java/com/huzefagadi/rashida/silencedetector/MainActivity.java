package com.huzefagadi.rashida.silencedetector;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Bundle;
import android.app.Activity;
import android.view.Menu;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;

public class MainActivity extends Activity {

    private static final String TAG = MainActivity.class.getSimpleName();
   private static final int RECORDER_BPP = 16;
    private static final String AUDIO_RECORDER_FILE_EXT_WAV = ".wav";
    private static final String AUDIO_RECORDER_FOLDER = "AudioRecorder";
    private static final String AUDIO_RECORDER_TEMP_FILE = "record_temp.raw";

    private static final int SILENCE_TIME = 200;
    private  int bufferSize ;
    private AudioRecord mAudioRecorder;
   // private boolean mRecordingStarted;
    private long fistMomentOfPause = 0;
    private int frequency = 32000; //44100;
   // private long fistMomentOfPause;
    private int channelConfiguration = AudioFormat.CHANNEL_IN_MONO;
    private int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
Thread thread;
    FileOutputStream os = null;

    /*int bufferSize ;
    int frequency = 8000; //8000;
    int channelConfiguration = AudioFormat.CHANNEL_IN_MONO;
    int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;*/
    boolean started = false;
    RecordAudio recordTask;

    short threshold=5000;

    boolean debug=false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.w(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //startAquisition();

        Button startAudio = (Button) findViewById(R.id.start);
        Button stopAudio = (Button) findViewById(R.id.stop);

        startAudio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                recordAudioWithSilenceDetection();
            }
        });


        stopAudio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopAquisition();
            }
        });
    }



    private boolean recordAudioWithSilenceDetection(){
        bufferSize = AudioRecord.getMinBufferSize(frequency, channelConfiguration, audioEncoding);

        mAudioRecorder= new AudioRecord( MediaRecorder.AudioSource.MIC, frequency,
                channelConfiguration, audioEncoding, bufferSize);

        String filename = getTempFilename();

        try {
            os = new FileOutputStream(filename);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        mAudioRecorder.startRecording();
        started = true;
        // do something long
        Runnable runnable = new Runnable() {
            short[] buffer = new short[bufferSize];
            @Override
            public void run() {
                while (started) { //loop runner
                    int bufferReadResult = mAudioRecorder.read(buffer, 0, bufferSize);
                    if (AudioRecord.ERROR_INVALID_OPERATION != bufferReadResult) {
                        //check signal
                        //put a threshold
                        int foundPeak = searchThreshold(buffer, threshold);
                        System.out.println("peak audio == "+foundPeak);
                        if (foundPeak > -1) { //found signal
                            //signal found (no silence)
                            Log.i("Proof", "wrote to file");
                            byte[] byteBuffer = ShortToByte(buffer, bufferReadResult);
                            try {
                                os.write(byteBuffer);
                            }
                            catch (IOException e) {
                                e.printStackTrace();
                            }
                            fistMomentOfPause = 0; //reset silence time when new sound is detected
                        } else {//count the time
                            //don't save signal (silence)
                            if (fistMomentOfPause == 0) {
                                fistMomentOfPause = System.currentTimeMillis(); //measure first moment of silence
                            }

                            if (fistMomentOfPause != 0 && System.currentTimeMillis() > fistMomentOfPause + SILENCE_TIME) {
                                Log.i("Proof", "Long silence detected");
                                //fistMomentOfPause = 0;
                            }
                            else {
                                Log.i("Proof", "wrote to file");
                                byte[] byteBuffer = ShortToByte(buffer, bufferReadResult);
                                try {
                                    os.write(byteBuffer);
                                }
                                catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }




                    }
                }
                fistMomentOfPause = 0; //reset
            }
        };
        thread = new Thread(runnable);
        thread.start();//running on a new thread
        return true;
    }

    @Override
    protected void onResume() {
        Log.w(TAG, "onResume");
        super.onResume();


    }

    @Override
    protected void onDestroy() {
        Log.w(TAG, "onDestroy");
        //stopAquisition();
        super.onDestroy();

    }

    public class RecordAudio extends AsyncTask<Void, Double, Void> {

        @Override
        protected Void doInBackground(Void... arg0) {
            Log.w(TAG, "doInBackground");
            try {

                String filename = getTempFilename();

                try {
                    os = new FileOutputStream(filename);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }


                bufferSize = AudioRecord.getMinBufferSize(frequency,
                        channelConfiguration, audioEncoding);

                AudioRecord audioRecord = new AudioRecord( MediaRecorder.AudioSource.MIC, frequency,
                        channelConfiguration, audioEncoding, bufferSize);

                short[] buffer = new short[bufferSize];

                audioRecord.startRecording();

                while (started) {
                    int bufferReadResult = audioRecord.read(buffer, 0,bufferSize);
                    if(AudioRecord.ERROR_INVALID_OPERATION != bufferReadResult){
                        //check signal
                        //put a threshold
                        int foundPeak=searchThreshold(buffer,threshold);

                        if (foundPeak>-1){ //found signal
                            //record signal
                            byte[] byteBuffer =ShortToByte(buffer,bufferReadResult);
                            try {
                                os.write(byteBuffer);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }else{//count the time
                            //don't save signal
                        }


                        //show results
                        //here, with publichProgress function, if you calculate the total saved samples,
                        //you can optionally show the recorded file length in seconds:      publishProgress(elsapsedTime,0);


                    }
                }

                audioRecord.stop();


                //close file
                try {
                    os.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                copyWaveFile(getTempFilename(),getFilename());
                deleteTempFile();


            } catch (Throwable t) {
                t.printStackTrace();
                Log.e("AudioRecord", "Recording Failed");
            }
            return null;

        } //fine di doInBackground






    /*
    @Override
    protected void onProgressUpdate(Double... values) {
        DecimalFormat sf = new DecimalFormat("000.0000");
        elapsedTimeTxt.setText(sf.format(values[0]));

    }
    */



    } //Fine Classe RecordAudio (AsyncTask)

    byte [] ShortToByte(short [] input, int elements) {
        int short_index, byte_index;
        int iterations = elements; //input.length;
        byte [] buffer = new byte[iterations * 2];

        short_index = byte_index = 0;

        for(/*NOP*/; short_index != iterations; /*NOP*/)
        {
            buffer[byte_index]     = (byte) (input[short_index] & 0x00FF);
            buffer[byte_index + 1] = (byte) ((input[short_index] & 0xFF00) >> 8);

            ++short_index; byte_index += 2;
        }

        return buffer;
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main,
                menu);
        return true;

    }

    int searchThreshold(short[]arr,short thr){
        int peakIndex;
        int arrLen=arr.length;
        for (peakIndex=0;peakIndex<arrLen;peakIndex++){
            if ((arr[peakIndex]>=thr) || (arr[peakIndex]<=-thr)){
                //se supera la soglia, esci e ritorna peakindex-mezzo kernel.

                return peakIndex;
            }
        }
        return -1; //not found
    }
    private String getFilename(){
        String filepath = Environment.getExternalStorageDirectory().getPath();
        File file = new File(filepath,AUDIO_RECORDER_FOLDER);

        if(!file.exists()){
            file.mkdir();
        }

        return (file.getAbsolutePath() + "/" + System.currentTimeMillis() + AUDIO_RECORDER_FILE_EXT_WAV);
    }


    private String getTempFilename(){
        File mainFile = Environment.getExternalStorageDirectory();
        File file = new File(mainFile,AUDIO_RECORDER_FOLDER);

        if(!file.exists()){
            boolean directoryCreated = file.mkdir();

        }

        File tempFile = new File(file,AUDIO_RECORDER_TEMP_FILE);

        // if(tempFile.exists())
        // tempFile.delete();

        return (tempFile.getAbsolutePath());
    }





    private void deleteTempFile() {
        File file = new File(getTempFilename());

        file.delete();
    }

    private void copyWaveFile(String inFilename, final String outFilename) {
        FileInputStream in = null;
        FileOutputStream out = null;
        long totalAudioLen = 0;
        long totalDataLen = totalAudioLen + 36;
        long longSampleRate = frequency;
        int channels = 1;
        long byteRate = RECORDER_BPP * frequency * channels/8;

        byte[] data = new byte[bufferSize];

        try {
            in = new FileInputStream(inFilename);
            out = new FileOutputStream(outFilename);
            totalAudioLen = in.getChannel().size();
            totalDataLen = totalAudioLen + 36;


            WriteWaveFileHeader(out, totalAudioLen, totalDataLen,
                    longSampleRate, channels, byteRate);



            while(in.read(data) != -1){
                out.write(data);
            }

            in.close();
            out.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        convertWaveToAmr(outFilename);

       /* File fileTmp = getApplicationContext().getCacheDir();

        try {
            FfmpegController fc = new FfmpegController(getApplicationContext(), fileTmp);
            final Clip clip = new Clip(outFilename);

            String outPutFileNameReal = outFilename.replace("wav","amr");
            final Clip outPutFile = new Clip(outPutFileNameReal);

            fc.convertToAmr(clip, outPutFile, new ShellUtils.ShellCallback() {
                @Override
                public void shellOut(String shellLine) {
                    System.out.println(shellLine);
                }

                @Override
                public void processComplete(int exitValue) {
                    System.out.println("DONE");
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }*/


    }

    private String getAMRFilename(){
        String filepath = Environment.getExternalStorageDirectory().getPath();
        File file = new File(filepath+ "/" + "M2converted.amr");
        try {
            file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return (file.getAbsolutePath());
    }
    public void convertWaveToAmr(String wavFilename)
    {

        AmrInputStream aStream = null ;
        InputStream inStream = null;
        OutputStream out = null;

        try {
            inStream = new FileInputStream(wavFilename);
            aStream= new AmrInputStream(inStream);
            File file = new File(getAMRFilename());
            out= new FileOutputStream(file);
            out.write(0x23);
            out.write(0x21);
            out.write(0x41);
            out.write(0x4D);
            out.write(0x52);
            out.write(0x0A);

            byte[] x = new byte[1024];
            int len;
            while ((len=aStream.read(x)) > 0) {
                out.write(x,0,len);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        finally
        {
            try {
                out.close();
                aStream.close();
                inStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }


    private void WriteWaveFileHeader(
            FileOutputStream out, long totalAudioLen,
            long totalDataLen, long longSampleRate, int channels,
            long byteRate) throws IOException {

        byte[] header = new byte[44];

        header[0] = 'R';  // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f';  // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16;  // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1;  // format = 1
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;
        header[24] = (byte) (longSampleRate & 0xff);
        header[25] = (byte) ((longSampleRate >> 8) & 0xff);
        header[26] = (byte) ((longSampleRate >> 16) & 0xff);
        header[27] = (byte) ((longSampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (channels * 16 / 8);  // block align
        header[33] = 0;
        header[34] = RECORDER_BPP;  // bits per sample
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);

        out.write(header, 0, 44);
    }


    public void resetAquisition() {
        Log.w(TAG, "resetAquisition");
        stopAquisition();
        //startButton.setText("WAIT");
        startAquisition();
    }

    public void stopAquisition() {
        Log.w(TAG, "stopAquisition");
        if (started) {
            started = false;
            thread.interrupt();
            copyWaveFile(getTempFilename(), getFilename());



            deleteTempFile();

        }
    }

    public void startAquisition(){
        Log.w(TAG, "startAquisition");
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            public void run() {

                //elapsedTime=0;
                started = true;
                recordTask = new RecordAudio();
                recordTask.execute();
                //startButton.setText("RESET");
            }
        }, 500);
    }







}