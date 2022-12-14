package com.bbogush.web_screen;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.content.Intent;
import android.net.http.SslError;
import android.os.Bundle;
import android.webkit.SslErrorHandler;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class ControlOtherActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        setContentView(R.layout.activity_control_other);
        WebView webView = findViewById(R.id.web_view);
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        webView.setWebViewClient(new IgnoreSslWebViewClient());
        String ip = "172.20.10.14";
        if (intent != null) {
            String inputIp = intent.getStringExtra("ip");
            if (inputIp != null) {
                ip = inputIp;
            }
        }
        webView.loadUrl(String.format("https://%s:8080", ip));
    }

    private class IgnoreSslWebViewClient extends WebViewClient {
        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            handler.proceed();
        }
    }
}