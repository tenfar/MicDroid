/* Mic.java
   An auto-tune app for Android

   Copyright (c) 2010 Ethan Chen

   Permission is hereby granted, free of charge, to any person obtaining a copy
   of this software and associated documentation files (the "Software"), to deal
   in the Software without restriction, including without limitation the rights
   to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
   copies of the Software, and to permit persons to whom the Software is
   furnished to do so, subject to the following conditions:

   The above copyright notice and this permission notice shall be included in
   all copies or substantial portions of the Software.

   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
   FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
   AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
   LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
   OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
   THE SOFTWARE.
 */

package com.intervigil.micdroid;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.media.AudioRecord;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.widget.CompoundButton.OnCheckedChangeListener;

public class Mic extends Activity {

	private static final int AUTOTALENT_CHUNK_SIZE = 8192;
	
	private static final float CONCERT_A = 440.0f;

	private static final int DEFAULT_SCALE_ROTATE = 0;
	private static final float DEFAULT_LFO_DEPTH = 0.0f;
	private static final float DEFAULT_LFO_RATE = 5.0f;
	private static final float DEFAULT_LFO_SHAPE = 0.0f;
	private static final float DEFAULT_LFO_SYM = 0.0f;
	private static final int DEFAULT_LFO_QUANT = 0;
	private static final int DEFAULT_FORM_CORR = 0;
	private static final float DEFAULT_FORM_WARP = 0.0f;
	
	private static final int AUDIORECORD_ILLEGAL_STATE = 4;
	private static final int AUDIORECORD_ILLEGAL_ARGUMENT = 5;
	private static final int WRITER_OUT_OF_SPACE = 6;
	private static final int RECORDING_GENERIC_EXCEPTION = 7;
	
	private StartupDialog startupDialog;
	
	private MicRecorder micRecorder;
	private MicWriter micWriter;
	
	// keep this queue separate from the message queues since this is a data channel
	private BlockingQueue<Sample> sampleQueue;
	
	/** Packet of audio to pass between reader and writer threads. */
	private class Sample {
    	public short[] buffer;
    	public int bufferSize;
    	public boolean isEnd;
    	
    	public Sample(short[] buffer, int bufferSize) {
    		this.buffer = buffer;
    		this.bufferSize = bufferSize;
    		this.isEnd = false;
    	}
    	
    	public Sample() {
    		this.isEnd = true;
    	}
    }
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        ((ToggleButton)findViewById(R.id.mic_toggle)).setOnCheckedChangeListener(mPowerBtnListener);
        startupDialog = new StartupDialog(this, R.string.startup_dialog_title, R.string.startup_dialog_text, R.string.startup_dialog_accept_btn);
    
        ((ToggleButton)findViewById(R.id.mic_toggle)).setChecked(false);
    	if (sampleQueue != null) {
    		sampleQueue.clear();
    	} else {
    		sampleQueue = new LinkedBlockingQueue<Sample>();
    	}
    	
    	startupDialog.show();
    	AudioHelper.configureRecorder(Mic.this);
    	PreferenceHelper.resetKeyDefault(Mic.this);
    	
    	android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
    }
    
    @Override
    protected void onStart() {
        Log.i(getPackageName(), "onStart()");
        super.onStart();
    }
    
    @Override
    protected void onResume() {
    	Log.i(getPackageName(), "onResume()");
    	super.onResume();
    }
    
    @Override
    protected void onPause() {
    	Log.i(getPackageName(), "onPause()");
    	super.onPause();
    }
    
    @Override
    protected void onStop() {
    	Log.i(getPackageName(), "onStop()");
    	super.onStop();
    }
    
    @Override
    protected void onDestroy() {
    	Log.i(getPackageName(), "onDestroy()");
    	super.onStop();
    	
    	if (micRecorder != null && micWriter != null) {
    		micRecorder.stopRunning();
    		
    		try {
    			micRecorder.join();
    			micWriter.join();
    		} catch (InterruptedException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		}
    	}

    	AutoTalent.destroyAutoTalent();
    }

    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        Log.i(getPackageName(), "onSaveInstanceState()");
        super.onSaveInstanceState(savedInstanceState);
    }
    
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
    	Log.i(getPackageName(), "onRestoreInstanceState()");
    	super.onRestoreInstanceState(savedInstanceState);
    }
    
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
    	Log.i(getPackageName(), "onConfigurationChanged");
    	super.onConfigurationChanged(newConfig);
    	
    	setContentView(R.layout.main);
    	
    	boolean isRecording = false;
    	if (micRecorder != null) {
	    	isRecording = micRecorder.isRunning();
    	}
    	ToggleButton micSwitch = (ToggleButton)findViewById(R.id.mic_toggle);
    	micSwitch.setChecked(isRecording);
    	micSwitch.setOnCheckedChangeListener(mPowerBtnListener);
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);

        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.options:
            	Intent preferencesIntent = new Intent(getBaseContext(), Preferences.class);
            	startActivity(preferencesIntent);
            	break;
            case R.id.playback:
            	Intent playbackIntent = new Intent(getBaseContext(), RecordingLibrary.class);
            	startActivity(playbackIntent);
            	break;
            case R.id.about:
            	DialogHelper.showWarning(Mic.this, R.string.about_title, R.string.about_text);
            	break;
            case R.id.help:
            	DialogHelper.showWarning(Mic.this, R.string.help_title, R.string.help_text);
            	break;
        }
        return true;
    }
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
    	super.onActivityResult(requestCode, resultCode, data);
    	
    	switch (requestCode) {
	    	case Constants.FILENAME_ENTRY_INTENT_CODE:
	    		if (resultCode == Activity.RESULT_OK) {
	    			String fileName = data.getStringExtra(getString(R.string.filename_entry_result));
	    			fileName = fileName + ".wav";
	    			new ProcessAutotalentTask().execute(fileName);
	    		} else if (resultCode == Activity.RESULT_CANCELED) {
	    			Toast.makeText(Mic.this, R.string.recording_save_canceled, Toast.LENGTH_SHORT).show();
	    		}
	    		break;
    		default:
    			break;
    	}
    }
    
    private Handler recordingErrorHandler = new Handler() {
    	// use the handler to receive error messages from the threads
    	@Override
    	public void handleMessage(Message msg) {
    		ToggleButton micToggle = (ToggleButton)findViewById(R.id.mic_toggle);
    		
    		switch (msg.what) {
	    		case AUDIORECORD_ILLEGAL_STATE:
	    			// received error message that AudioRecord was started without being properly initialized
	    			micToggle.setChecked(false);
	    			DialogHelper.showWarning(Mic.this, R.string.audiorecord_exception_title, R.string.audiorecord_exception_warning);
	    			break;
	    		case AUDIORECORD_ILLEGAL_ARGUMENT:
	    			// received error message that AudioRecord was started with bad sample rate/buffer size
	    			micToggle.setChecked(false);
	    			DialogHelper.showWarning(Mic.this, R.string.audiorecord_exception_title, R.string.audiorecord_exception_warning);
	    			break;
	    		case WRITER_OUT_OF_SPACE:
	    			// received error that the writer is out of SD card space
	    			micRecorder.stopRunning();
	    			micToggle.setChecked(false);
	    			DialogHelper.showWarning(Mic.this, R.string.writer_out_of_space_title, R.string.writer_out_of_space_warning);
	    			sampleQueue.clear();
	    			break;
	    		case RECORDING_GENERIC_EXCEPTION:
	    			// some sort of error occurred in the threads, don't know what yet, send a log!
	    			micRecorder.stopRunning();
	    			micToggle.setChecked(false);
	    			DialogHelper.showWarning(Mic.this, R.string.recording_exception_title, R.string.recording_exception_warning);
	    			sampleQueue.clear();
	    			break;
    		}
    	}
    };

    private class ProcessAutotalentTask extends AsyncTask<String, Void, Void> {
    	private WaveReader reader;
    	private WaveWriter writer;
    	private ProgressDialog spinner;
    	
    	public ProcessAutotalentTask() {
    		spinner = new ProgressDialog(Mic.this);
    		spinner.setCancelable(false);
    	}
    	
    	@Override
    	protected void onPreExecute() {
    		spinner.setMessage(getString(R.string.autotalent_progress_msg));
    		spinner.show();
    	}
    	
		@Override
		protected Void doInBackground(String... params) {
			// maybe ugly but we only pass one string in anyway
			String fileName = params[0];

			try {
				reader = new WaveReader(
						((MicApplication)getApplication()).getOutputDirectory(), 
						getString(R.string.default_recording_name));
				reader.openWave();
				writer = new WaveWriter(
						((MicApplication)getApplication()).getLibraryDirectory(), 
						fileName,
						reader.getSampleRate(), reader.getChannels(), reader.getPcmFormat());
				writer.createWaveFile();
			} catch (IOException e) {
				// can't create our readers and writers for some reason!
				// TODO: real error handling
				e.printStackTrace();
			}
			
			updateAutoTalentPreferences();
			
			short[] buf = new short[AUTOTALENT_CHUNK_SIZE];
			while (true) {
				try {
					int samplesRead = reader.readShort(buf, AUTOTALENT_CHUNK_SIZE);
					if (samplesRead > 0) {
						AutoTalent.processSamples(buf, samplesRead);
						writer.write(buf, samplesRead);
					} else {
						break;
					}
				} catch (IOException e) {
					// failed to read/write to wave file
					// TODO: real error handling
					e.printStackTrace();
				}
			}
			
			try {
				reader.closeWaveFile();
				writer.closeWaveFile();
				AutoTalent.destroyAutoTalent();
			} catch (IOException e) {
				// failed to close out our files correctly
				// TODO: real error handling
				e.printStackTrace();
			}
			
			// insert file into media store
			File file = new File(((MicApplication)getApplication()).getLibraryDirectory() + File.separator + fileName);
			MediaStoreHelper.insertFile(Mic.this, file);

			return null;
		}
		
		@Override
		protected void onPostExecute(Void unused) {
			spinner.dismiss();
			Toast.makeText(Mic.this, R.string.recording_save_success, Toast.LENGTH_SHORT).show();
		}
    }
    
    private OnCheckedChangeListener mPowerBtnListener = new OnCheckedChangeListener() {
    	public void onCheckedChanged(CompoundButton btn, boolean isChecked) {
    		if (!canWriteToSdCard()) {
        		btn.setChecked(false);
    			DialogHelper.showWarning(Mic.this, R.string.no_external_storage_title, R.string.no_external_storage_warning);
        	}
    		else if (!AudioHelper.isValidRecorderConfiguration(Mic.this)) {
    			btn.setChecked(false);
    			DialogHelper.showWarning(Mic.this, R.string.unconfigured_audio_title, R.string.unconfigured_audio_warning);
    		}
    		else {
				if (btn.isChecked()) {
					sampleQueue.clear();
					micRecorder = new MicRecorder(sampleQueue);
					micRecorder.setPriority(Thread.MAX_PRIORITY);
					micRecorder.start();

		        	micWriter = new MicWriter(sampleQueue);
		        	micWriter.start();
		        	Toast.makeText(getBaseContext(), R.string.recording_started_toast, Toast.LENGTH_SHORT).show();
				} else {
					if (micRecorder.isRunning()) {
						// only do this if it was running, otherwise an error message triggered the check state change
						try {
							micRecorder.stopRunning();
							micRecorder.join();
							micWriter.join();
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						sampleQueue.clear();
						Toast.makeText(getBaseContext(), R.string.recording_finished_toast, Toast.LENGTH_SHORT).show();
						
		    			Intent saveFileIntent = new Intent(getBaseContext(), FileNameEntry.class);
						startActivityForResult(saveFileIntent, Constants.FILENAME_ENTRY_INTENT_CODE);
					}
				}
    		}
		}
    };
    
    private void updateAutoTalentPreferences() {
    	char key = PreferenceHelper.getKey(Mic.this);
    	float fixedPitch = PreferenceHelper.getFixedPitch(Mic.this);
    	float fixedPull = PreferenceHelper.getPullToFixedPitch(Mic.this);
    	float pitchShift = PreferenceHelper.getPitchShift(Mic.this);
    	float strength = PreferenceHelper.getCorrectionStrength(Mic.this);
    	float smooth = PreferenceHelper.getCorrectionSmoothness(Mic.this);
    	float mix = PreferenceHelper.getMix(Mic.this);
    	
    	AutoTalent.instantiateAutoTalent(PreferenceHelper.getSampleRate(Mic.this));
    	AutoTalent.initializeAutoTalent(CONCERT_A, key, fixedPitch, fixedPull, 
    			strength, smooth, pitchShift, DEFAULT_SCALE_ROTATE, 
    			DEFAULT_LFO_DEPTH, DEFAULT_LFO_RATE, DEFAULT_LFO_SHAPE, DEFAULT_LFO_SYM, DEFAULT_LFO_QUANT, 
    			DEFAULT_FORM_CORR, DEFAULT_FORM_WARP, mix);
    }
    
    private class MicWriter extends Thread {
    	private final BlockingQueue<Sample> queue;
    	private WaveWriter writer;
    	
    	public MicWriter(BlockingQueue<Sample> q) {
    		queue = q;
    	}
    	
		public void run() {
			try {
				writer = new WaveWriter(
						((MicApplication)getApplication()).getOutputDirectory(),
						getString(R.string.default_recording_name), 
						PreferenceHelper.getSampleRate(Mic.this), 
						AudioHelper.getChannelConfig(Constants.DEFAULT_CHANNEL_CONFIG), 
						AudioHelper.getPcmEncoding(Constants.DEFAULT_PCM_FORMAT));
				writer.createWaveFile();
    		
				while (true) {
					Sample sample = queue.take();
					if (!sample.isEnd) {
						writer.write(sample.buffer, sample.bufferSize);
					}
					else {
						shutdown();
		    			return;
					}
				}
			} catch (IOException e) {
				// problem writing to the buffer, usually means we're out of space
				e.printStackTrace();
				shutdown();
				
				Message msg = recordingErrorHandler.obtainMessage(WRITER_OUT_OF_SPACE);
				recordingErrorHandler.sendMessage(msg);
			} catch (InterruptedException e) {
				// problem removing from the queue
				e.printStackTrace();
				shutdown();
				
				Message msg = recordingErrorHandler.obtainMessage(RECORDING_GENERIC_EXCEPTION);
				recordingErrorHandler.sendMessage(msg);
			}
		}
		
		private void shutdown() {
			try {
				writer.closeWaveFile();
				writer = null;
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
    }
    
    private class MicRecorder extends Thread {
    	private final BlockingQueue<Sample> queue;
    	private boolean isRunning;
    	private AudioRecord recorder;
    	    	
    	public MicRecorder(BlockingQueue<Sample> q) {
    		queue = q;
    	}
    	
    	public boolean isRunning() {
    		return this.isRunning;
    	}
    	
    	public void stopRunning() {
    		this.isRunning = false;
    	}
    	
    	public void run() {
    		isRunning = true;
    		
    		try {
				recorder = AudioHelper.getRecorder(Mic.this);
	
	    		short[] buffer = new short[AudioHelper.getRecorderBufferSize(Mic.this)];
	    		recorder.startRecording();
	    		
	    		
	    		while (isRunning) {
					int numSamples = recorder.read(buffer, 0, buffer.length);
					queue.put(new Sample(buffer, numSamples));
	    		}
	    		
	    		shutdown();
    		} catch (IllegalStateException e) {
    			// problem with audiorecord not being initialized properly
    			e.printStackTrace();
    			shutdown();
    			
    			Message msg = recordingErrorHandler.obtainMessage(AUDIORECORD_ILLEGAL_STATE);
    			recordingErrorHandler.sendMessage(msg);
    		} catch (IllegalArgumentException e) {
    			// problem with audiorecord being given a bad sample rate/buffer size
    			e.printStackTrace();
    			shutdown();
    			
    			Message msg = recordingErrorHandler.obtainMessage(AUDIORECORD_ILLEGAL_ARGUMENT);
    			recordingErrorHandler.sendMessage(msg);
    		} catch (InterruptedException e) {
    			// problem putting sample on the queue
				e.printStackTrace();
				shutdown();
				
				Message msg = recordingErrorHandler.obtainMessage(RECORDING_GENERIC_EXCEPTION);
				recordingErrorHandler.sendMessage(msg);
    		}
    	}
    	
    	private void shutdown() {
    		isRunning = false;
    		if (recorder != null) {
	    		recorder.release();
	    		recorder = null;
    		}
    		
    		try {
    			Sample endMarker = new Sample();
				queue.put(endMarker);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	}
    }
    
    private static boolean canWriteToSdCard() {
    	return Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
    }
}