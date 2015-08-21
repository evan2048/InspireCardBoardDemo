package com.evan2048.evancardboard;

import java.util.Timer;
import java.util.TimerTask;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.usb.UsbManager;
import android.media.MediaCodec;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import dji.midware.data.manager.P3.ServiceManager;
import dji.midware.usb.P3.DJIUsbAccessoryReceiver;
import dji.midware.usb.P3.UsbAccessoryService;
import dji.sdk.api.DJIDrone;
import dji.sdk.api.DJIError;
import dji.sdk.api.Battery.DJIBatteryProperty;
import dji.sdk.api.Camera.DJICameraSettingsTypeDef.CameraPreviewResolutionType;
import dji.sdk.api.DJIDroneTypeDef.DJIDroneType;
import dji.sdk.api.Gimbal.DJIGimbalAttitude;
import dji.sdk.api.Gimbal.DJIGimbalRotation;
import dji.sdk.api.Gimbal.DJIGimbalTypeDef.GimbalWorkMode;
import dji.sdk.interfaces.DJIBatteryUpdateInfoCallBack;
import dji.sdk.interfaces.DJIExecuteResultCallback;
import dji.sdk.interfaces.DJIGerneralListener;
import dji.sdk.interfaces.DJIGimbalUpdateAttitudeCallBack;
import dji.sdk.interfaces.DJIReceivedVideoDataCallBack;
import dji.sdk.widget.DjiGLSurfaceView;

public class MainActivity extends Activity implements OnClickListener,SurfaceHolder.Callback {
	
	private static final String TAG = "EvanCardBoardMainActivity";  //debug TAG
	//DJI
	private final DJIDroneType DJI_DRONE_TYPE=DJIDroneType.DJIDrone_Inspire1;
	private static boolean isDJIAoaStarted = false;  //DJIAoa
	private DJIReceivedVideoDataCallBack mReceivedVideoDataCallBack;
	private DJIGimbalUpdateAttitudeCallBack mGimbalUpdateAttitudeCallBack;
	private DJIBatteryUpdateInfoCallBack mBattryUpdateInfoCallBack;
	private int gimbalPitchAngle=0;  //current dji gimbal angle
	private int gimbalRollAngle=0;
	private int gimbalYawAngle=0;
	//Sensor
    private SensorManager mSensorManager;
    private SensorEventListener mSensorEventListener;
    private Sensor accelerometer;
    private Sensor magnetometer;
    private float gravity[];
    private float geomagnetic[];
    private int phoneInitAzimuthAngle=0;  //phone init offset angle
    private int phoneInitPitchAngle=0;
    private int phoneInitRollAngle=0;
    private int phoneAzimuthAngle=0;  //phone current angle
    private int phonePitchAngle=0;
    private int phoneRollAngle=0;
    private int phoneOutputAzimuthAngle=0;  //phone output to gimbal angle
    private int phoneOutputPitchAngle=0;
    private int phoneOutputRollAngle=0;
    private boolean isTracking=false;
	//Others
	private MediaCodec mediaCodec;
	
	//print log message
	private void showLOG(String str)
	{
		Log.e(TAG, str);
	}
	
	//show Toast
	private void showToast(String str)
	{
		Toast.makeText(this, str, Toast.LENGTH_SHORT).show();
	}
	
	//init UI
    private DjiGLSurfaceView mDjiGLSurfaceView;
//	private SurfaceView mSurfaceView;
//	private SurfaceHolder mSurfaceHolder;
//	private Surface mSurface;
    private LinearLayout centerLinearLayout;  //hold start button to center screen
	private Button startButton;  //start video preview
	private Button centralizeGimbalButton;
	private TextView batteryTextView;
    private void initUIControls()
    {
        mDjiGLSurfaceView=(DjiGLSurfaceView)findViewById(R.id.mDjiSurfaceView);
    	//mSurfaceView=(SurfaceView)findViewById(R.id.mSurfaceView);
		//mSurfaceHolder = mSurfaceView.getHolder();
        centerLinearLayout=(LinearLayout)findViewById(R.id.centerLinearLayout);
        startButton=(Button)findViewById(R.id.startButton);
        centralizeGimbalButton=(Button)findViewById(R.id.centralizeGimablButton);
        batteryTextView=(TextView)findViewById(R.id.batteryTextView);
        //set listener
        startButton.setOnClickListener(this);
        centralizeGimbalButton.setOnClickListener(this);
		//mSurfaceHolder.addCallback(this);
        //customize
        centralizeGimbalButton.setText("StartTracking");
        centralizeGimbalButton.setEnabled(false);
		batteryTextView.setText("");
    }
	
    //init sensors
	private void initSensors()
	{
        //get Sensors data
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        mSensorEventListener = new SensorEventListener()
        {
            @Override
            public void onSensorChanged(SensorEvent event)
            {
                if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
                {
                    gravity = event.values;
                }
                if(event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
                {
                    geomagnetic = event.values;
                }
                if(gravity != null && geomagnetic != null)
                {
                    float r[] = new float[9];
                    float outR[] = new float[9];
                    float i[] = new float[9];
                    boolean success = SensorManager.getRotationMatrix(r, i, gravity, geomagnetic);
                    if(success == true)
                    {
                        float orientation[] = new float[3];
                        //Using the camera (Y axis along the camera's axis) in landscape mode
                        SensorManager.remapCoordinateSystem(r, SensorManager.AXIS_X, SensorManager.AXIS_Z, outR);
                        SensorManager.getOrientation(outR, orientation);
                        //get raw angle
                        phoneAzimuthAngle=(int)(orientation[0]*180/Math.PI);
                        phonePitchAngle=(int)(orientation[1]*180/Math.PI);
                        phoneRollAngle=(int)(orientation[2]*180/Math.PI);
                        
                        //change phoneAzimuthAngle range from 0~180,-180~0 to 0~360
                        if(phoneAzimuthAngle<0)
                        {
                        	phoneAzimuthAngle=360+phoneAzimuthAngle;
                        }
                        //calculate output offset
                        phoneOutputAzimuthAngle=phoneAzimuthAngle-phoneInitAzimuthAngle;
                        //change back range from 0~360 to 0~180,-180~0
                        if(phoneOutputAzimuthAngle<0)
                        {
                        	phoneOutputAzimuthAngle=360+phoneOutputAzimuthAngle;
                        }
                        if(phoneOutputAzimuthAngle>180)
                        {
                        	phoneOutputAzimuthAngle=phoneOutputAzimuthAngle-360;
                        }
                        
                        //change phonePitchAngle range from 90~-30 to -90~30
                        phonePitchAngle=-phonePitchAngle;
                        phoneOutputPitchAngle=phonePitchAngle-phoneInitPitchAngle;
                        
                        //change phoneRollAngle center -90 to 90
                        phoneRollAngle=phoneRollAngle+90;
                        phoneOutputRollAngle=phoneRollAngle-phoneInitRollAngle;
                        
                        //showLOG(" Azimut: "+(int)phoneInitAzimuthAngle+" "+(int)phoneAzimuthAngle+" "+(int)phoneOutputAzimuthAngle);
                        //showLOG(" Pitch: "+(int)phoneInitPitchAngle+" "+(int)phonePitchAngle+" "+(int)phoneOutputPitchAngle);
                        //showLOG(" Roll: "+(int)phoneInitRollAngle+" "+(int)phoneRollAngle+" "+(int)phoneOutputRollAngle);
                        //showLOG(" Azimut: "+(int)phoneInitAzimuthAngle+" "+(int)phoneAzimuthAngle+" "+(int)phoneOutputAzimuthAngle+" Pitch: "+(int)phoneInitPitchAngle+" "+(int)phonePitchAngle+" "+(int)phoneOutputPitchAngle+" Roll: "+(int)phoneInitRollAngle+" "+(int)phoneRollAngle+" "+(int)phoneOutputRollAngle);
                        
                        //showLOG("Outputs:Azimut="+phoneOutputAzimuthAngle+" Pitch="+phoneOutputPitchAngle+" Roll="+phoneOutputRollAngle);
                    }
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy)
            {
            	
            }
        };
        //Register sensor listeners
        mSensorManager.registerListener(mSensorEventListener, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
        mSensorManager.registerListener(mSensorEventListener, magnetometer, SensorManager.SENSOR_DELAY_FASTEST);
        //mSensorManager.registerListener(mSensorEventListener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);  //SENSOR_DELAY_NORMAL:10Hz
        //mSensorManager.registerListener(mSensorEventListener, magnetometer, SensorManager.SENSOR_DELAY_NORMAL);
	}
    
    //button click
	@Override
	public void onClick(View v)
	{
        switch(v.getId())
        {
        case R.id.startButton:
        {
        	startDJICamera();
        	centerLinearLayout.setVisibility(View.INVISIBLE);  //hide center region controls
        	//customize other controls
        	centralizeGimbalButton.setEnabled(true);
            break;
        }
        case R.id.centralizeGimablButton:
        {
        	//update phone offset
        	phoneInitAzimuthAngle=phoneAzimuthAngle;
        	phoneInitPitchAngle=phonePitchAngle;
        	phoneInitRollAngle=phoneRollAngle;
        	if(isTracking==false)
        	{
    			//set Gimbal free mode
            	DJIDrone.getDjiGimbal().setGimbalWorkMode(GimbalWorkMode.Free_Mode, new DJIExecuteResultCallback()
            	{
    				@Override
    				public void onResult(DJIError result)
    				{
    					if(result.errorCode==DJIError.RESULT_OK)
    					{
    			        	//start tracking thread
    			    		new Thread()
    			    		{
    			    			public void run()
    			    			{
    			    				while(true)
    			    				{
    			    					//DJIGimbalRotation mPitch = new DJIGimbalRotation(true,true,true, phoneOutputPitchAngle);
    			    					//DJIGimbalRotation mRoll = new DJIGimbalRotation(true,true,true, phoneOutputRollAngle);
    			      	                DJIGimbalRotation mYaw = new DJIGimbalRotation(true,true,true, phoneOutputAzimuthAngle);
    			      	                
    			      	                //DJIDrone.getDjiGimbal().updateGimbalAttitude(mPitch,null,null);
    			      	                //DJIDrone.getDjiGimbal().updateGimbalAttitude(null, mRoll, null);
    			      	                DJIDrone.getDjiGimbal().updateGimbalAttitude(null,null,mYaw);
    			      	                try
    			      	                {
    										sleep(500);
    									}
    			      	                catch (InterruptedException e)
    			      	                {
    										e.printStackTrace();
    									}
    			    				}
    			    			}
    			    		}.start();
    			    		runOnUiThread(new Runnable()
    			    		{
								public void run()
								{
									centralizeGimbalButton.setText("Centralize Gimbal");
								}
							});
    					}
    				}
    			});
            	isTracking=true;
        	}
        	break;
        }
        default:
        {
            break;
        }
        }
	}
    
	//OpenCVLoader callback
    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    showLOG("OpenCV Manager loaded successfully");
                    break;
                }
                default:
                {
                    super.onManagerConnected(status);
                    break;
                }
            }
        }
    };
	
	//init OpenCVLoader
	private boolean initOpenCVLoader()
	{
        if (!OpenCVLoader.initDebug())
        {
            // Handle initialization error
        	showLOG("init buildin OpenCVLoader error,going to use OpenCV Manager");
        	OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this, mLoaderCallback);
        	return false;
        }
        else
        {
        	showLOG("init buildin OpenCVLoader success");
        	return true;
        }
	}
	
	//activate DJI SDK(if not,DJI SDK can't use)
	private void activateDJISDK()
	{
        new Thread()
        {
            public void run()
            {
                try
                {
                    DJIDrone.checkPermission(getApplicationContext(), new DJIGerneralListener()
                    {
                        @Override
                        public void onGetPermissionResult(int result)
                        {
                        	//result=0 is success
                            showLOG("DJI SDK onGetPermissionResult = "+result);
                            showLOG("DJI SDK onGetPermissionResultDescription = "+DJIError.getCheckPermissionErrorDescription(result));
                            if(result!=0)
                            {
                            	showToast(getString(R.string.dji_sdk_activate_error)+":"+DJIError.getCheckPermissionErrorDescription(result));
                            }
                        }
                    });
                }
                catch(Exception e)
                {
                	showLOG("activateDJISDK() Exception");
                	showToast("activateDJISDK() Exception");
                    e.printStackTrace();
                }
            }
        }.start();
	}
	
	//start DJIAoa
	private void startDJIAoa()
	{
        if(isDJIAoaStarted)
        {
            //Do nothing
        	showLOG("DJIAoa aready started");
        }
        else
        {
            ServiceManager.getInstance();
            UsbAccessoryService.registerAoaReceiver(this);
        	isDJIAoaStarted = true;
        	showLOG("DJIAoa start success");
        }
        Intent aoaIntent = getIntent();
        if(aoaIntent != null)
        {
            String action = aoaIntent.getAction();
            if(action==UsbManager.ACTION_USB_ACCESSORY_ATTACHED || action == Intent.ACTION_MAIN)
            {
                Intent attachedIntent = new Intent();
                attachedIntent.setAction(DJIUsbAccessoryReceiver.ACTION_USB_ACCESSORY_ATTACHED);
                sendBroadcast(attachedIntent);
            }
        }
	}
	
	//init DJI SDK
    private void initDJISDK()
    {
    	startDJIAoa();
    	activateDJISDK();
        // The SDK initiation for Inspire 1 or Phantom 3 Professional.
        DJIDrone.initWithType(this.getApplicationContext(), DJI_DRONE_TYPE);
        DJIDrone.connectToDrone(); // Connect to the drone
    }
	
    //check DJI camera connect status
	private Timer checkCameraConnectionTimer = new Timer();
	class CheckCameraConnectionTask extends TimerTask
	{
		@Override
		public void run()
		{
			if(checkCameraConnectState()==true)
			{
				runOnUiThread(new Runnable()
				{
					public void run()
					{
						startButton.setBackgroundResource(R.drawable.start_green);
						startButton.setClickable(true);
					}
				});
			}
			else
			{
				runOnUiThread(new Runnable()
				{
					public void run()
					{
						startButton.setBackgroundResource(R.drawable.start_gray);
						startButton.setClickable(false);
					}
				});
			}
		}
	}
    private boolean checkCameraConnectState(){
        //check connection
        boolean cameraConnectState = DJIDrone.getDjiCamera().getCameraConnectIsOk();
        if(cameraConnectState)
        {
        	//showLOG("DJI Camera connect ok");
        	return true;
        }
        else
        {
        	//showLOG("DJI Camera connect failed");
        	return false;
        }
    }
    
    //init DJI camera
    private void initDJICamera()
    {
        //check camera status every 3 seconds
        checkCameraConnectionTimer.schedule(new CheckCameraConnectionTask(), 1000, 3000);
    }
	
	//start DJI camera
	private void startDJICamera()
	{
//		try
//		{
//			mediaCodec = MediaCodec.createDecoderByType("video/avc");
//		}
//		catch (IOException e)
//		{
//			e.printStackTrace();
//		}
//	    MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc",320,240);  //Creates a minimal video format
//	    mediaCodec.configure(mediaFormat, mSurface, null, 0);
//	    mediaCodec.start();
		
		if(DJI_DRONE_TYPE==DJIDroneType.DJIDrone_Vision)
		{
			mDjiGLSurfaceView.setStreamType(CameraPreviewResolutionType.Resolution_Type_320x240_15fps);
		}
    	mDjiGLSurfaceView.start();
	    
    	//decode video data
    	mReceivedVideoDataCallBack=new DJIReceivedVideoDataCallBack()
    	{
			@Override
			public void onResult(byte[] videoBuffer, int size)
			{
				//solution1
//				ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
//		        int inputBufferIndex = mediaCodec.dequeueInputBuffer(-1);
//		        if (inputBufferIndex >= 0)
//		        {
//			        ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
//			        inputBuffer.clear();
//			        inputBuffer.put(videoBuffer);
//			        mediaCodec.queueInputBuffer(inputBufferIndex, 0, size, System.currentTimeMillis(), 0);
//		        }
//		        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
//		        int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo,0);
//		        while(outputBufferIndex >= 0)
//		        {
//		        	mediaCodec.releaseOutputBuffer(outputBufferIndex, true);
//		        	outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
//		        }
		        
				//solution2
//				if(mSurface!=null)  //Prevent crash onDestroy()
//				{
//					// if API level <= 20, get input and output buffer arrays here
//					ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
//					ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();
//			        int inputBufferIndex = mediaCodec.dequeueInputBuffer(-1);
//			        if(inputBufferIndex >= 0)
//			        {
//			        	// fill inputBuffers[inputBufferIndex] with valid data
//				        inputBuffers[inputBufferIndex].clear();
//				        inputBuffers[inputBufferIndex].put(videoBuffer);
//				        mediaCodec.queueInputBuffer(inputBufferIndex, 0, size, System.currentTimeMillis(), 0);
//			        }
//			        
//			        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
//			        int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo,0);
//			        while(outputBufferIndex >= 0)
//			        {
//			        	ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
//			        	// outputBuffer is ready to be processed or rendered.
//			        	
//			        	mediaCodec.releaseOutputBuffer(outputBufferIndex, true);
//			        	outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo,0);
//			        	//showLOG("Rendering");
//			        }
//				}
		        
				//showLOG(videoBuffer.toString()+"  "+size);
				mDjiGLSurfaceView.setDataToDecoder(videoBuffer, size);
				
			}
		};
		
        mGimbalUpdateAttitudeCallBack = new DJIGimbalUpdateAttitudeCallBack()
        {
            @Override
            public void onResult(DJIGimbalAttitude attitude)
            {
            	gimbalPitchAngle=(int)attitude.pitch;
            	gimbalRollAngle=(int)attitude.roll;
            	gimbalYawAngle=(int)attitude.yaw;
            	//showLOG(attitude.toString());
            }
        };
        
        mBattryUpdateInfoCallBack = new DJIBatteryUpdateInfoCallBack(){
            @Override
            public void onResult(final DJIBatteryProperty state)
            {
    			runOnUiThread(new Runnable()
    			{
					public void run()
					{
						batteryTextView.setText(getString(R.string.battery)+":"+state.remainPowerPercent+"%");
					}
				});
            }
        };
        
		DJIDrone.getDjiCamera().setReceivedVideoDataCallBack(mReceivedVideoDataCallBack);
		DJIDrone.getDjiGimbal().setGimbalUpdateAttitudeCallBack(mGimbalUpdateAttitudeCallBack);
		DJIDrone.getDjiBattery().setBatteryUpdateInfoCallBack(mBattryUpdateInfoCallBack);
		DJIDrone.getDjiGimbal().startUpdateTimer(0);
		DJIDrone.getDjiBattery().startUpdateTimer(2000);
	}
	
	//destroy DJI camera
	private void destroyDJICamera()
	{
		checkCameraConnectionTimer.cancel();
		if(DJIDrone.getDjiCamera()!=null)
		{
            DJIDrone.getDjiCamera().setReceivedVideoDataCallBack(null);
            DJIDrone.getDjiGimbal().setGimbalUpdateAttitudeCallBack(null);
            DJIDrone.getDjiBattery().setBatteryUpdateInfoCallBack(null);
            DJIDrone.getDjiGimbal().stopUpdateTimer();
            DJIDrone.getDjiBattery().stopUpdateTimer();
            mDjiGLSurfaceView.destroy();
            destroyMediaCodec();
		}
	}
	
	//destroy mediaCodec
	private void destroyMediaCodec()
	{
		if(mediaCodec!=null)
		{
			mediaCodec.stop();
			mediaCodec.release();
			mediaCodec=null;
		}
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.activity_main);
		//onCreate init
		initUIControls();
		initOpenCVLoader();
		initDJISDK();
		initDJICamera();
		initSensors();
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		ServiceManager.getInstance().pauseService(false); // Resume the DJIAoa service
	}

	@Override
	protected void onPause()
	{
		super.onPause();
		ServiceManager.getInstance().pauseService(true); // Pause the DJIAoa service
	}

	@Override
	protected void onDestroy()
	{
		destroyDJICamera();
        DJIDrone.disconnectToDrone();
        showLOG("MainActivity onDestroy()");
		super.onDestroy();
		android.os.Process.killProcess(android.os.Process.myPid());
	}
	
	//press again to exit
	private static boolean needPressAgain = false;
	private Timer ExitTimer = new Timer();
	class ExitCleanTask extends TimerTask
	{
		@Override
		public void run()
		{               
			needPressAgain = false;
		}
	}
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event)
	{
        if (keyCode == KeyEvent.KEYCODE_BACK)
        {
            if (needPressAgain) {
            	needPressAgain = false;
                finish();
            } 
            else 
            {
            	needPressAgain = true;
            	showToast(getString(R.string.pressAgainExitString));
                ExitTimer.schedule(new ExitCleanTask(), 2000);
            }
            return true;
        }
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder)
	{
		
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
	{
		//mSurface = holder.getSurface();
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder)
	{
		//mSurface = null;
	}
}
