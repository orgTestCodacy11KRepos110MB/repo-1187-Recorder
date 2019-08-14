package com.github.xianyuecn.recorder;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.webkit.ConsoleMessage;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    static private final String LogTag="MainActivity";

    private WebView webView;
    private EditText logs;
    private RecordAppJsBridge jsBridge;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        webView = findViewById(R.id.main_webview);
        logs=findViewById(R.id.main_logs);
        logs.append("日志输出已开启");

        //******调用核心方法*********************
        //注入JsBridge, 实现api接口，简单demo无视4.2以下版本
        jsBridge=new RecordAppJsBridge(this, webView, permissionReq, Log);
        //*******以下内容无关紧要*****************

        //设置基本信息，如开启js、storage、权限处理
        initWebSet();
    }



    //权限处理 小功能就不用我的Android-UsesPermission库了，手撸一个简洁版的
    private RecordAppJsBridge.UsesPermission permissionReq= new RecordAppJsBridge.UsesPermission() {
        @Override
        public void Request(String[] keys, final Runnable True, final Runnable False) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PermissionCall=new Runnable() {
                    @Override
                    public void run() {
                        if(HasPermission) {
                            True.run();
                        }else{
                            //无权限
                            False.run();
                        }
                    }
                };
                MainActivity.this.requestPermissions(keys, ReqCode);
            }else{
                //无需授权
                True.run();
            }
        }
    };

    private RecordAppJsBridge.ILog Log=new RecordAppJsBridge.ILog() {
        @Override
        public void i(String tag, String msg) {
            android.util.Log.i(tag, msg);

            logs.append("\n\n["+time()+"][i]["+tag+"]"+msg);
        }

        @Override
        public void e(String tag, String msg) {
            android.util.Log.e(tag, msg);

            logs.append("\n\n["+time()+"][e]["+tag+"]"+msg);
        }

        private String time(){
            SimpleDateFormat formatter=new SimpleDateFormat   ("HH:mm:ss", Locale.CHINA);
            return formatter.format(new Date());
        }
    };



    @Override
    protected void onDestroy() {
        jsBridge.close();

        super.onDestroy();
    }




    /**
     * WebView基本信息设置
     */
    @SuppressLint("SetJavaScriptEnabled")
    private void initWebSet(){
        WebSettings set=webView.getSettings();
        set.setJavaScriptEnabled(true);
        set.setDefaultTextEncodingName("utf-8");
        set.setDomStorageEnabled(true);

        File cacheDir=getExternalCacheDir();
        File fileDir=getExternalFilesDir(null);
        if(cacheDir==null){
            cacheDir=getCacheDir();
        }
        if(fileDir==null){
            fileDir=getFilesDir();
        }
        set.setAppCacheEnabled(true);
        set.setAppCachePath(cacheDir.getAbsolutePath());
        set.setDatabaseEnabled(true);
        set.setDatabasePath(fileDir.getAbsolutePath());
        set.setGeolocationEnabled(true);
        set.setGeolocationDatabasePath(fileDir.getAbsolutePath());


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            set.setMediaPlaybackRequiresUserGesture(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            set.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        webView.setWebViewClient(new WebViewClient(){
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if(url.startsWith("http")){
                    return false;
                }
                return super.shouldOverrideUrlLoading(view, url);
            }
        });

        //网页权限请求处理
        webView.setWebChromeClient(new WebChrome());

        webView.setBackgroundColor(0xffff6600);

        String url="https://xiangyuecn.github.io/Recorder/app-support-sample/";
        webView.loadUrl(url);
        Log.i(LogTag, "打开页面:"+url);
    }










    /**
     * 录音权限处理
     */
    public class WebChrome extends WebChromeClient{
        @Override
        public void onPermissionRequest(final PermissionRequest request) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                String key="";
                final String[] types=request.getResources();
                for(String s:types) {
                    if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(s)) {
                        key="android.permission.RECORD_AUDIO";
                    }
                }

                if(key.length()==0){
                    request.deny();
                    return;
                }

                permissionReq.Request(new String[]{key}, new Runnable() {
                    @Override
                    public void run() {
                        request.grant(types);
                    }
                }, new Runnable() {
                    @Override
                    public void run() {
                        request.deny();
                    }
                });
            }
        }

        @Override
        public boolean onConsoleMessage(ConsoleMessage msg) {
            String str=msg.sourceId()+":"+msg.lineNumber()+"\n"+msg.message();
            switch (msg.messageLevel()){
                case ERROR:
                    Log.e("Console", str);
                    break;
                default:
                    Log.i("Console", str);
            }

            return super.onConsoleMessage(msg);
        }
    }
    private Runnable PermissionCall;
    private boolean HasPermission;

    static private final int ReqCode=123;
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if(requestCode==ReqCode && PermissionCall!=null) {
            StringBuilder noGrant=new StringBuilder();
            for(int i=0; i<permissions.length;i++){
                String item=permissions[i];
                if(grantResults[i]!= PackageManager.PERMISSION_GRANTED){
                    if(noGrant.length()>0)noGrant.append(",");
                    noGrant.append(item);
                }
            }

            HasPermission=noGrant.length()==0;
            if(!HasPermission){
                Toast.makeText(MainActivity.this, "请给app权限:"+noGrant, Toast.LENGTH_LONG).show();
            }
            PermissionCall.run();
            PermissionCall=null;
        }
    }
}