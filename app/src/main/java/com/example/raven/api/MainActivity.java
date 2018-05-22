package com.example.raven.api;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.media.SoundPool;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;


import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.FileProvider;
import android.support.v4.content.pm.ActivityInfoCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.zxing.activity.CaptureActivity;
import com.google.zxing.decoding.Intents;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static android.content.Context.WIFI_SERVICE;

public class MainActivity extends AppCompatActivity {
    private static final int QRscan_REQUEST_CODE = 0x03;
    private static final int QRscan_RESULT_OK = 0xA1;
    private static final int CAMERA_OK=0xA2;
    private WifiManager mWiFiManager;
    private String netWorkType;
    private String netWorkName;
    private String password;
    private static final int WIFICIPHER_NOPASS = 0;
    private static final int WIFICIPHER_WEP = 1;
    private static final int WIFICIPHER_WPA = 2;
    private static final int WIFICIPHER_WPA2= 3;
    private String urlPath="http://ec2-18-219-194-179.us-east-2.compute.amazonaws.com:8080/scantest";
    private String urlUploadImg="http://ec2-18-219-194-179.us-east-2.compute.amazonaws.com:8080/ipfsbool";
    private static final String CONTENT_TYPE = "multipart/form-data"; //内容类型
    FrameLayout fl_preview;
    static FrameLayout mSurfaceViewFrame;
    private static Camera mCamera;
    private static CameraPreview mPreview;
    static String TAG = MainActivity.class.getSimpleName();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        fl_preview = (FrameLayout) findViewById(R.id.camera_preview);
        mWiFiManager = (WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
        openScan();

        //CheckWrite();


    }
    public void CheckWrite(){  //写文件权限检查
        if(ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            AutoCamera();
        }
        else
        {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},1);
            AutoCamera();
        }
    }

    public void openScan(){ //开镜头权限检查
        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            Intent intent = new Intent(MainActivity.this, CaptureActivity.class);
            startActivityForResult(intent, QRscan_REQUEST_CODE);
        }else{
            //申请权限
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA},1);
            openScan();
        }
    }

    public void AutoCamera(){
        initView(fl_preview);
        startAutoCamera();
    }

    public void initView(FrameLayout surfaceViewFrame)
    {
        mSurfaceViewFrame = surfaceViewFrame;
    }
    Handler mHandler = new Handler()
    {
        @Override
        public void handleMessage(Message msg) {
            switch(msg.what)
            {
                case 1:
                    Log.v(TAG, "开始拍照");
                    initCarema();
                    break;
                case 2:
                    mCamera.autoFocus(new Camera.AutoFocusCallback() {
                        @Override
                        public void onAutoFocus(boolean success, Camera camera) {
                            // 从Camera捕获图片
                            Log.v(TAG, "自动聚焦"+success);
                            mCamera.takePicture(null, null, mPicture);
                            //回调扫描器
                            openScan();
                        }
                    });
                    break;
            }
        }
    };

    public void startAutoCamera()
    {
        mHandler.sendEmptyMessageDelayed(1, 5*1000); //5s 后开始启动相机
    }

    private void initCarema()
    {
        Log.v(TAG, "initCarema");
        try
        {
            if(mCamera==null)
            {
                Log.v(TAG, "camera=null");
                mCamera = getCameraInstance();
                mPreview = new CameraPreview(MainActivity.this, mCamera);
                mSurfaceViewFrame.removeAllViews();
                mSurfaceViewFrame.addView(mPreview);
            }
            Log.v(TAG, mCamera==null ?"mCamera is null":"mCamera is not null");
            mCamera.startPreview();
            mHandler.sendEmptyMessageDelayed(2, 3*1000); //3s后拍照
        }
        catch (Exception ex){
            Log.e("init camera error=",ex.getMessage());
            initCarema();
        }
    }
    public static Camera getCameraInstance() {
        Camera c = null;
        try {
            c = Camera.open();
            c.setDisplayOrientation(90);
            Camera.Parameters mParameters = c.getParameters();
            //可以用得到当前所支持的照片大小，然后
            List<Camera.Size> ms = mParameters.getSupportedPictureSizes();
            mParameters.setPictureSize(ms.get(0).width, ms.get(0).height);  //默认最大拍照取最大清晰度的照片
            c.setParameters(mParameters);
        } catch (Exception e) {
            Log.d(TAG, "打开Camera失败");
        }
        return c;
    }

    private Camera.PictureCallback mPicture = new Camera.PictureCallback() {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            // 获取Jpeg图片，并保存在sd卡上
            String path="/sdcard/Download/";
            File dirF = new File(path);
            if(!dirF.exists())
            {
                dirF.mkdirs();
            }
            final String filePath=path + System.currentTimeMillis()+ ".jpg";
            File pictureFile = new File(filePath);
            try {
                FileOutputStream fos = new FileOutputStream(pictureFile);
                fos.write(data);
                fos.close();
                Log.d(TAG, "保存图片成功");

            } catch (Exception e) {
                Log.d(TAG, "保存图片失败");
                e.printStackTrace();
            }
            releaseCarema();
            new Thread(){
                @Override
                public void run() {
                    uploadFile(urlUploadImg,filePath);
                }
            }.start();
        }
    };
    public void releaseCarema()
    {
        if(mCamera!=null){
            mCamera.stopPreview();
            mCamera.release();
            mCamera=null;

        }
    }
    private void uploadFile(String uploadUrl,String uploadFilePath) {
        String end = "\r\n";
        String twoHyphens = "--";
        String boundary = "******";
        try {
            URL url = new URL(uploadUrl);
            HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
            httpURLConnection.setDoInput(true);
            httpURLConnection.setDoOutput(true);
            httpURLConnection.setUseCaches(false);
            httpURLConnection.setRequestMethod("POST");
            httpURLConnection.setRequestProperty("Connection", "Keep-Alive");
            httpURLConnection.setRequestProperty("Charset", "UTF-8");
            httpURLConnection.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
            String filename=uploadFilePath.substring(uploadFilePath.lastIndexOf("\\")+1);
            DataOutputStream dos = new DataOutputStream(httpURLConnection.getOutputStream());
            dos.writeBytes(twoHyphens + boundary + end);
            dos.writeBytes("Content-Disposition: form-data; name=\"campic\"; filename=\""+ filename + "\"" + end);
            dos.writeBytes(end);
            // 文件通过输入流读到Java代码中-++++++++++++++++++++++++++++++`````````````````````````
            FileInputStream fis = new FileInputStream(uploadFilePath);
            byte[] buffer = new byte[8192]; // 8k
            int count = 0;
            while ((count = fis.read(buffer)) != -1) {
                dos.write(buffer, 0, count);

            }
            fis.close();
            System.out.println("file send to server............");
            dos.writeBytes(end);
            dos.writeBytes(twoHyphens + boundary + twoHyphens + end);
            dos.flush();

            // 读取服务器返回结果
            InputStream is = httpURLConnection.getInputStream();
            InputStreamReader isr = new InputStreamReader(is, "utf-8");
            BufferedReader br = new BufferedReader(isr);
            String result = br.readLine();
            Log.e("upload img result=",result);
            dos.close();
            is.close();
            if(result.equals("true")){
                Uri notification = Uri.parse("android.resource://"+MainActivity.this.getPackageName()+"/"+R.raw.sendover);
                Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
                r.play();
            }
        } catch (Exception e) {
            e.printStackTrace();
            setTitle(e.getMessage());
        }

    }



    protected void onActivityResult(int requestCode, int resultCode, Intent data) { //二维码扫描回调
        super.onActivityResult(requestCode, resultCode, data);
        try
        {
            switch (requestCode) {
                case QRscan_REQUEST_CODE:
                    if (data!=null){
                        Bundle bundle = data.getExtras();
                        String scanResult = bundle.getString("qr_scan_result");
                        Log.e("QRcode=",scanResult);
                        if (scanResult.indexOf("QRCODE")>-1){
                            //网络请求发送参数
                            Uri notification = Uri.parse("android.resource://"+MainActivity.this.getPackageName()+"/"+R.raw.start);
                            Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
                            r.play();
                            final JSONObject jsondata = new JSONObject();
                            jsondata.put("barCode", scanResult.substring(7));
                            jsondata.put("extraInfo", "test from china");
                            jsondata.put("privateKey", "012345678");
                            new AsyncTask<String, Void, String>() {
                                @Override
                                protected String doInBackground(String... params) {
                                    StringBuffer result = new StringBuffer();
                                    try {
                                        URL url = new URL(params[0]);
                                        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                                        connection.setDoInput(true);// 输入流默认就为开启状态，可不设
                                        connection.setDoOutput(true);// 设置使用输出流
                                        connection.setRequestMethod("POST");// 默认为Get方法
                                        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                                        OutputStreamWriter osw = new OutputStreamWriter(
                                                connection.getOutputStream(), "utf-8");
                                        BufferedWriter bw = new BufferedWriter(osw);
                                        //bw.write("{\"barCode\":\"0123456789\",\"extraInfo\":\"test1\"}");
                                        bw.write(jsondata.toString());
                                        bw.flush();
                                        InputStream is = connection.getInputStream();
                                        InputStreamReader isr = new InputStreamReader(is,"utf-8");//不设置编码有返回数据有中文时会出现乱码
                                        BufferedReader br = new BufferedReader(isr);
                                        String line;
                                        while ((line = br.readLine()) != null) {
                                            result.append(line);
                                        }
                                        br.close();
                                        isr.close();
                                        is.close();
                                    } catch (MalformedURLException e) {
                                        // TODO Auto-generated catch block
                                        e.printStackTrace();
                                    } catch (IOException e) {
                                        // TODO Auto-generated catch block
                                        e.printStackTrace();
                                    }
                                    return result.toString();
                                }

                                @Override
                                protected void onPostExecute(String result) {
                                    // TODO Auto-generated method stub
                                    Log.e("result=",result);
                                    Uri notification = Uri.parse("android.resource://"+MainActivity.this.getPackageName()+"/"+R.raw.sendover);
                                    Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
                                    r.play();
                                    //创建定时拍照任务
                                    CheckWrite();//检查写权限，没问题就启动相机进入自动拍照
                                }
                            }.execute(urlUploadImg);
                        }
                        else if (scanResult.indexOf("WIFI")>-1) {//wifi code
                            splitResultAndConnect(scanResult);
                        }
                    }
                    break;

            }
            //openScan();
        }
        catch (Exception ex){
            Log.e("connect wifi error=",ex.getMessage());
            ex.printStackTrace();
            Intent intent = new Intent(MainActivity.this, CaptureActivity.class);
            startActivityForResult(intent, QRscan_REQUEST_CODE);
        }
    }

    private void splitResultAndConnect(String result) {
        String[] arrar = result.split(";");
        int netId=0;
        if (result.contains("P") && result.contains("T")) {
            //result的结果为：WIFI:T:wpa;S:xiaohuangren;P:test1230;
            netWorkType = arrar[0].split(":")[2];
            Log.i("wifi", "加密方式：" + netWorkType);
            netWorkName = arrar[2].split(":")[1];
            Log.i("wifi",  netWorkName);
            password = arrar[1].split(":")[1].replace("\"","");
            Log.i("wifi", password);

            if (TextUtils.equals("WEP", netWorkType.toUpperCase())) {
                //netWorkConnect.Connect(netWorkName, password, NetWorkConnect.WifiCipherType.WIFICIPHER_WEP);
                netId=mWiFiManager.addNetwork(createWifiConfig(netWorkName,password,1));
            } else if (TextUtils.equals("WPA", netWorkType.toUpperCase())) {
                //netWorkConnect.Connect(netWorkName, password, NetWorkConnect.WifiCipherType.WIFICIPHER_WPA);
                netId=mWiFiManager.addNetwork( createWifiConfig(netWorkName,password,2));
            }else if (TextUtils.equals("WPA2", netWorkType.toUpperCase())) {
                //netWorkConnect.Connect(netWorkName, password, NetWorkConnect.WifiCipherType.WIFICIPHER_WPA);
                netId=mWiFiManager.addNetwork( createWifiConfig(netWorkName,password,3));
            }
        } else {
            netWorkName = arrar[0].split(":")[2];
            Log.i("wifi", "网络名称：" + netWorkName);
            netId=mWiFiManager.addNetwork(createWifiConfig(netWorkName,password,0));
        }
        Log.d("wifiId","id=" +netId);
        boolean enable = mWiFiManager.enableNetwork(netId, true);
        Log.d("wifiEnable", "enable: " + enable);
        boolean reconnect = mWiFiManager.reconnect();
        Log.d("wifiConnect", "reconnect: " + reconnect);
    }

    private WifiConfiguration createWifiConfig(String ssid, String password, int type) {
        //初始化WifiConfiguration
        WifiConfiguration config = new WifiConfiguration();
        config.allowedAuthAlgorithms.clear();
        config.allowedGroupCiphers.clear();
        config.allowedKeyManagement.clear();
        config.allowedPairwiseCiphers.clear();
        config.allowedProtocols.clear();

        //指定对应的SSID
        config.SSID = "\"" + ssid + "\"";

        //如果之前有类似的配置
        WifiConfiguration tempConfig = isExist(ssid);
        if(tempConfig != null) {
            //则清除旧有配置
            mWiFiManager.removeNetwork(tempConfig.networkId);
        }

        //不需要密码的场景
        if(type == WIFICIPHER_NOPASS) {
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            //以WEP加密的场景
        } else if(type == WIFICIPHER_WEP) {
            config.hiddenSSID = true;
            config.wepKeys[0]= "\""+password+"\"";
            config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
            config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED);
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            config.wepTxKeyIndex = 0;

        } else if(type == WIFICIPHER_WPA) {
            config.preSharedKey = "\""+password+"\"";
            config.hiddenSSID = true;
            config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
            config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
            config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
            config.status = WifiConfiguration.Status.ENABLED;
        }else if (type==WIFICIPHER_WPA2) {
            config.preSharedKey = "\"" + password + "\"";
            config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
            config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
            config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
            config.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
            config.status = WifiConfiguration.Status.ENABLED;

        }

        return config;
    }

    private WifiConfiguration isExist(String ssid) {
        List<WifiConfiguration> configs = mWiFiManager.getConfiguredNetworks();

        for (WifiConfiguration config : configs) {
            if (config.SSID.equals("\""+ssid+"\"")) {
                return config;
            }
        }
        return null;
    }


}
