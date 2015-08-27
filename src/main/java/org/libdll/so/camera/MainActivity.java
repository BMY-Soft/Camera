/*	Camera for Android
	Copyright 2015 libdll.so

	This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any later version.

	This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
*/

package org.libdll.so.camera;

import android.app.AlertDialog;
import android.app.NativeActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.gesture.GestureLibrary;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;


public class MainActivity extends ActionBarActivity implements View.OnClickListener {

	private SurfaceView surfaceview;
	private Preview preview;
	//private ImageView ivFocus;

	//private native static int umask(int mask);

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		((Button)findViewById(R.id.button)).setOnClickListener(this);
		//imageview = (ImageView)findViewById(R.id.imageview);
		surfaceview = (SurfaceView)findViewById(R.id.preview);
		preview = new Preview(surfaceview);
		//System.loadLibrary("c");
		//umask(022);
	}


	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.menu_main, menu);
		return true;
	}

	private void show_about() {
		try {
			PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
			(new AlertDialog.Builder(MainActivity.this)).setTitle(getString(R.string.about)).setMessage("libdll.so Camera " + info.versionName + "\n" + getString(R.string.license_text)).show();
		} catch(PackageManager.NameNotFoundException e) {
			e.printStackTrace();
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Log.d("methon", "onOptionsItemSelected(" + item.toString() + ")");
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();

		switch(id) {
			case R.id.action_about:
				show_about();
				return true;
				//break;
			case R.id.action_settings:
				Intent intent = new Intent(this, SettingsActivity.class);
				startActivity(intent);
				return true;
		}

		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onClick(View v) {
		//new AlertDialog.Builder(this).setTitle("aaabbb").setMessage("Hi").show();
		//System.err.println("aaaaa");
		//Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
		//System.err.println(i);
		//new AlertDialog.Builder(this).setTitle("Camera").setMessage(i.toString()).show();
		preview.take_picture();
		//startActivityForResult(i, 1);

	}

	//public boolean onTouchEvent(MotionEvent event) {
	//	System.err.printf("methon: onTouchEvent(%s)\n", event.toString());
	//	return super.onTouchEvent(event);
	//}

	class Preview implements SurfaceHolder.Callback {
		private SurfaceHolder holder;
		private Camera camera;
		private Camera.PictureCallback pictureCallback = new Camera.PictureCallback() {
			// 该方法用于处理拍摄后的照片数据
			@Override
			public void onPictureTaken(byte[] data, Camera camera) {
				SharedPreferences config = getSharedPreferences("org.libdll.so.camera_preferences", MODE_WORLD_READABLE);
				String ringtone_name = null;
				if(config.getBoolean("enable_notification", false)) {
					ringtone_name = config.getString("notifications_taken_ringtone", null);
					if(ringtone_name != null && ringtone_name.isEmpty()) ringtone_name = null;
				}
				Vibrator vibrator = null;
				if(config.getBoolean("enable_vibrate", false)) {
					vibrator = (Vibrator)getSystemService(VIBRATOR_SERVICE);
				}
				Log.d("Data length", String.valueOf(data.length));
				if(ringtone_name != null && config.getString("notifications_time", "none").equals("Taken")) {
					play_ringtone(ringtone_name);
				}
				if(vibrator != null && config.getString("vibrate_time", "none").equals("Taken")) {
					vibrator.vibrate(100);
				}
				Bitmap camera_bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
				//setResult(20);
				try {
					//Runtime.getRuntime().loadLibrary("libc.so");
					String dir = config.getString("save_directory", "/data/Pictures/");
					if(dir.charAt(dir.length() - 1) != '/') dir += "/";
					String filepath = dir + get_file_name_from_date();
					FileOutputStream f = new FileOutputStream(filepath);
					Runtime.getRuntime().exec("chmod 644 " + filepath);
					//chmod(filepath, 0644);
					//setPosixFilePermissions(0644);
					BufferedOutputStream bos = new BufferedOutputStream(f);
					camera_bitmap.compress(Bitmap.CompressFormat.JPEG, 95, bos);
					bos.flush();
					bos.close();
					Toast.makeText(MainActivity.this, R.string.saved, Toast.LENGTH_SHORT).show();
					if(ringtone_name != null && config.getString("notifications_time", "none").equals("Saved")) {
						play_ringtone(ringtone_name);
					}
					if(vibrator != null && config.getString("vibrate_time", "none").equals("Saved")) {
						vibrator.vibrate(100);
					}
				} catch(FileNotFoundException e) {
					e.printStackTrace();
					save_error(e.getLocalizedMessage());
				} catch(IOException e) {
					e.printStackTrace();
					save_error(e.getLocalizedMessage());
				}
			}
		};

		private void play_ringtone(String name) {
			Log.d("method", "play_ringtone(" + name +")");
			Uri notification = Uri.parse(name);
			RingtoneManager.getRingtone(MainActivity.this, notification).play();
		}

		public Preview(SurfaceView v) {
			//super(context);
			//holder = getHolder();
			holder = v.getHolder();
			holder.addCallback(this);
			//System.loadLibrary("c");
			//System.load("/system/lib/libc.so");
		}

		private void save_error(String msg) {
			(new AlertDialog.Builder(MainActivity.this)).setTitle(getString(R.string.save_error)).setMessage(msg).show();

		}

		public void surfaceCreated(SurfaceHolder holder) {
			camera = Camera.open();
			if(camera == null) {
				//new AlertDialog.Builder(MainActivity.this).setTitle("Camera").setMessage("No such device").show();
				//AlertDialog.Builder dialog = new AlertDialog.Builder(MainActivity.this);
				//dialog.setTitle("Camera").setMessage("No such device");
				//dialog.create().show();

				AlertDialog dialog = (new AlertDialog.Builder(MainActivity.this)).setTitle("Camera").setMessage("No such device").create();
				DialogInterface.OnCancelListener on_cancel = new DialogInterface.OnCancelListener() {
					@Override
					public void onCancel(DialogInterface dialog) {
					/*
						{
							Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
							startActivity(intent);
							SharedPreferences config = getSharedPreferences("org.libdll.so.camera_preferences", MODE_WORLD_READABLE);
							if(config.getBoolean("notifications_taken", false)) {
								Log.d("ringtone", "enabled");
								String name = config.getString("notifications_taken_ringtone", null);
								if(name == null || name.isEmpty()) return;
								Log.d("notifications_taken_...", name);
								Uri notification = Uri.parse(name);
								RingtoneManager.getRingtone(MainActivity.this, notification).play();
							}
						}
					*/
						MainActivity.this.finish();
					}
				};
				dialog.setOnCancelListener(on_cancel);
				dialog.show();
				return;
			}

			try {
				camera.setPreviewDisplay(holder);
				camera.setDisplayOrientation(90);
			} catch(IOException e) {
				e.printStackTrace();
				camera.release();
				camera = null;
			}
		}

		public void surfaceDestroyed(SurfaceHolder holder) {
			if(camera == null) return;
			camera.release();
		}

		public void surfaceChanged(final SurfaceHolder holder, int format, int w, int h) {
			//try {
			if(camera == null) return;
				camera.startPreview();
				ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(
					ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
				//ivFocus.setScaleType(ImageView.ScaleType.CENTER);
				//addContentView(ivFocus, layoutParams);
				//ivFocus.setVisibility(VISIBLE);

				camera.autoFocus(new Camera.AutoFocusCallback()
				{
					@Override
					public void onAutoFocus(boolean success, Camera camera)
					{
						if (success) {
							// success 为 true 表示对焦成功
						}
					}
				});
			//}
		}

		public void take_picture() {
			if(camera == null) return;
			try {
				camera.takePicture(null, null, pictureCallback);
			} catch(RuntimeException e) {
				e.printStackTrace();
				(new AlertDialog.Builder(MainActivity.this)).setTitle(R.string.take_error).setMessage(e.getMessage()).show();
			}
		}

		public String get_file_name_from_date() {
			//Date date = new Date();
			//DateFormat formatter = SimpleDateFormat.getDateTimeInstance();
			SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd.HH-mm-ss");
			return formatter.format(new Date()) + ".jpg";
		}
	}
}
