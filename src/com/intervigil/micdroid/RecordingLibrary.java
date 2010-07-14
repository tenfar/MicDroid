/* RecordingLibrary.java

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
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;

public class RecordingLibrary extends Activity {

	private ListView library;
	private RecordingAdapter libraryAdapter;
	private ArrayList<Recording> recordings;

	/**
     * Called when the activity is starting.  This is where most
     * initialization should go: calling setContentView(int) to inflate
     * the activity's UI, etc.
     * 
     * @param   savedInstanceState	Activity's saved state, if any.
     */
    @SuppressWarnings("unchecked")
	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.recording_library);

        this.library = (ListView)findViewById(R.id.recording_library_list);
        this.library.setOnItemClickListener(libraryClickListener);
        this.library.setOnItemLongClickListener(libraryLongClickListener);
        
        Object savedRecordings = getLastNonConfigurationInstance();
        if (savedRecordings == null) {
	        recordings = new ArrayList<Recording>();
	        this.libraryAdapter = new RecordingAdapter(this, R.layout.library_row, recordings);
	        this.library.setAdapter(libraryAdapter);
			new LoadRecordingsTask().execute((Void)null);
        } else {
        	recordings = (ArrayList<Recording>)savedRecordings;
        	this.libraryAdapter = new RecordingAdapter(this, R.layout.library_row, recordings);
	        this.library.setAdapter(libraryAdapter);
	        this.libraryAdapter.notifyDataSetChanged();
        }
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
    protected void onSaveInstanceState(Bundle outState) {
        Log.i(getPackageName(), "onSaveInstanceState()");
        super.onSaveInstanceState(outState);
    }
    
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
    	Log.i(getPackageName(), "onRestoreInstanceState()");
    	super.onRestoreInstanceState(savedInstanceState);
    }
    
    @Override
    public Object onRetainNonConfigurationInstance() {
    	final ArrayList<Recording> recordingList = recordings;
    	return recordingList;
    }
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
    	super.onActivityResult(requestCode, resultCode, data);
    	
    	switch (requestCode) {
	    	case Constants.PLAYER_INTENT_CODE:
	    		if (resultCode == Constants.RESULT_FILE_DELETED) {
	    			new LoadRecordingsTask().execute((Void)null);
	    		}
	    		break;
    		default:
    			break;
    	}
    }
    
    private OnItemClickListener libraryClickListener = new OnItemClickListener() {
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			Recording r = (Recording)parent.getItemAtPosition(position);
	    	Intent playIntent = new Intent(getBaseContext(), RecordingPlayer.class);
			Bundle playData = new Bundle();
			playData.putString(Constants.PLAY_DATA_RECORDING_NAME, r.getRecordingName());
			playIntent.putExtras(playData);
			startActivityForResult(playIntent, Constants.PLAYER_INTENT_CODE);
		}
	};
	
	private OnItemLongClickListener libraryLongClickListener = new OnItemLongClickListener() {
		public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
			
			return false;
		}
	};
    
    private class RecordingAdapter extends ArrayAdapter<Recording> {		
		public RecordingAdapter(Context context, int textViewResourceId, List<Recording> objects) {
			super(context, textViewResourceId, objects);
		}

		@Override
		public View getView (int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                LayoutInflater vi = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                view = vi.inflate(R.layout.library_row, parent, false);
            }
            
            Recording r = this.getItem(position);
            if (r != null) {
            	((TextView)view.findViewById(R.id.row_first_line)).setText("Name: " + r.getRecordingName());
                ((TextView)view.findViewById(R.id.row_second_line)).setText("Length: " + r.getRecordingLength());
            }

            return view;
        }
    }
    
    private class LoadRecordingsTask extends AsyncTask<Void, Void, Void> {
    	// Async load all the recordings already in the directory
    	private final ProgressDialog spinner = new ProgressDialog(RecordingLibrary.this);
    	WaveReader reader;
    	
    	@Override
    	protected void onPreExecute() {
    		recordings.clear();
    		this.spinner.setMessage("Loading");
    		this.spinner.show();
    	}
    	
		@Override
		protected Void doInBackground(Void... params) {
			// move old recordings if there are any
			migrateOldRecordings();
			
			File libraryDir = new File(((MicApplication)getApplication()).getLibraryDirectory());
			File[] waveFiles = libraryDir.listFiles();
			Recording r = null;
			
			if (waveFiles != null) {
				for (int i = 0; i < waveFiles.length; i++) {
					if (waveFiles[i].isFile()) {
						reader = new WaveReader(waveFiles[i]);
						
						try {
							reader.openWave();
							r = new Recording(waveFiles[i].getName(), reader.getLength());
							recordings.add(r);
							Log.i("RecordingLibrary", String.format("Added recording %s to library", r.getRecordingName()));
							reader.closeWaveFile();
							reader = null;
						} catch (IOException e) {
							// yes I know it sucks that we do control flow with an exception here, fix it later
							Log.i("RecordingLibrary", String.format("Non-wave file %s found in library directory!", waveFiles[i].getName()));
						}
					}
				}
			}
			
			return null;
		}
		
		@Override
		protected void onPostExecute(Void result) {
			this.spinner.dismiss();
			libraryAdapter.notifyDataSetChanged();
		}
		
		private void migrateOldRecordings() {
			File oldLibraryDir = new File(((MicApplication)getApplication()).getOldLibraryDirectory());
			File[] waveFiles = oldLibraryDir.listFiles();
			
			if (waveFiles != null) {
				for (int i = 0; i < waveFiles.length; i++) {
					if (waveFiles[i].isFile()) {
						File destination = new File(((MicApplication)getApplication()).getLibraryDirectory() + File.separator + waveFiles[i].getName());
						waveFiles[i].renameTo(destination);
						Log.i("RecordingLibrary", String.format("Moved recording %s to new library directory!", waveFiles[i].getName()));
					}
				}
			}
		}
    }
}
