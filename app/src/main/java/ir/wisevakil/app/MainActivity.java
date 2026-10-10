package ir.wisevakil.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;import android.app.AlertDialog;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class MainActivity extends AppCompatActivity {
    private SwipeRefreshLayout swipeRefresh;

    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    private boolean pageLoadFailed = false;
    private boolean offlineDialogShowing = false;
    private boolean networkAvailable = false;

    private WebView webView;

    private ValueCallback<Uri[]> filePathCallback;

    private ActivityResultLauncher<String> permissionLauncher;

    private ActivityResultLauncher<Intent> fileChooserLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);

        setupPermissionLauncher();
        setupFileChooserLauncher();
        setupWebView();
        getOnBackPressedDispatcher().addCallback(this,
                new OnBackPressedCallback(true) {

                    @Override
                    public void handleOnBackPressed() {

                        if (webView.canGoBack()) {
                            webView.goBack();
                        } else {
                            finish();
                        }
                    }
                });
        webView.loadUrl("https://wisevakil.ir");
        registerNetworkCallback();
    }

    private void setupWebView() {

        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);

        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                if (swipeRefresh != null) {
                    swipeRefresh.setRefreshing(false);
                }

                pageLoadFailed = false;
            }

            @Override
            public void onReceivedError(
                    WebView view,
                    WebResourceRequest request,
                    android.webkit.WebResourceError error
            ) {
                super.onReceivedError(view, request, error);

                if (request.isForMainFrame()) {
                    pageLoadFailed = true;

                    if (swipeRefresh != null) {
                        swipeRefresh.setRefreshing(false);
                    }

                    if (!hasInternet()) {
                        showOfflineDialog();
                    }
                }
            }
        });        swipeRefresh = findViewById(R.id.swipeRefresh);

        swipeRefresh.setOnRefreshListener(() -> {
            if (hasInternet()) {
                webView.reload();
            } else {
                swipeRefresh.setRefreshing(false);
                showOfflineDialog();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public boolean onShowFileChooser(
                    WebView webView,
                    ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams) {

                MainActivity.this.filePathCallback = filePathCallback;

                checkFilePermissionAndOpenChooser(fileChooserParams);

                return true;
            }
        });
    }
    private boolean hasInternet() {
        ConnectivityManager cm =
                (ConnectivityManager) getSystemService(
                        Context.CONNECTIVITY_SERVICE
                );

        if (cm == null) return false;

        Network network = cm.getActiveNetwork();

        if (network == null) return false;

        NetworkCapabilities capabilities =
                cm.getNetworkCapabilities(network);

        return capabilities != null
                && capabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET
        )
                && capabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_VALIDATED
        );
    }
    private void showOfflineDialog() {
        if (isFinishing() || offlineDialogShowing) return;

        offlineDialogShowing = true;

        new AlertDialog.Builder(this)
                .setTitle("عدم اتصال به اینترنت")
                .setMessage(
                        "اتصال اینترنت برقرار نیست. " +
                                "لطفاً اینترنت خود را بررسی کنید."
                )
                .setPositiveButton("تلاش مجدد", (dialog, which) -> {
                    offlineDialogShowing = false;

                    if (hasInternet()) {
                        pageLoadFailed = false;
                        webView.reload();
                    } else {
                        showOfflineDialog();
                    }
                })
                .setOnDismissListener(dialog ->
                        offlineDialogShowing = false
                )
                .show();
    }
    private void registerNetworkCallback() {
        connectivityManager =
                (ConnectivityManager) getSystemService(
                        Context.CONNECTIVITY_SERVICE
                );

        if (connectivityManager == null) return;

        networkCallback =
                new ConnectivityManager.NetworkCallback() {

                    @Override
                    public void onAvailable(Network network) {
                        runOnUiThread(() -> {
                            networkAvailable = true;
                            checkConnectionRestored();
                        });
                    }

                    @Override
                    public void onLost(Network network) {
                        runOnUiThread(() -> {
                            networkAvailable = false;

                            if (!hasInternet()) {
                                showOfflineDialog();
                            }
                        });
                    }

                    @Override
                    public void onCapabilitiesChanged(
                            Network network,
                            NetworkCapabilities capabilities
                    ) {
                        runOnUiThread(() -> {
                            if (hasInternet()) {
                                networkAvailable = true;
                                checkConnectionRestored();
                            }
                        });
                    }
                };

        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(
                        NetworkCapabilities.NET_CAPABILITY_INTERNET
                )
                .build();

        connectivityManager.registerNetworkCallback(
                request,
                networkCallback
        );
    }

    private void checkConnectionRestored() {
        if (networkAvailable && pageLoadFailed && hasInternet()) {
            pageLoadFailed = false;

            if (swipeRefresh != null) {
                swipeRefresh.setRefreshing(true);
            }

            webView.reload();
        }
    }
    @Override
    protected void onDestroy() {
        if (connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(
                        networkCallback
                );
            } catch (IllegalArgumentException ignored) {
            }
        }

        super.onDestroy();
    }

    private void setupPermissionLauncher() {

        permissionLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.RequestPermission(),
                        isGranted -> {

                            if (filePathCallback == null)
                                return;

                            openFileChooser();
                        }
                );
    }

    private void setupFileChooserLauncher() {

        fileChooserLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.StartActivityForResult(),
                        result -> {

                            if (filePathCallback == null)
                                return;

                            Uri[] results = null;

                            if (result.getResultCode() == Activity.RESULT_OK) {

                                Intent data = result.getData();

                                if (data != null) {

                                    if (data.getClipData() != null) {

                                        int count = data.getClipData().getItemCount();

                                        results = new Uri[count];

                                        for (int i = 0; i < count; i++) {
                                            results[i] =
                                                    data.getClipData()
                                                            .getItemAt(i)
                                                            .getUri();
                                        }

                                    } else if (data.getData() != null) {

                                        results = new Uri[]{
                                                data.getData()
                                        };
                                    }
                                }
                            }

                            filePathCallback.onReceiveValue(results);

                            filePathCallback = null;
                        }
                );
    }

    private void checkFilePermissionAndOpenChooser(
            WebChromeClient.FileChooserParams params) {

        // Android 13+
        if (android.os.Build.VERSION.SDK_INT >= 33) {

            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_MEDIA_IMAGES
            ) != PackageManager.PERMISSION_GRANTED) {

                permissionLauncher.launch(
                        Manifest.permission.READ_MEDIA_IMAGES
                );

                return;
            }
        }

        openFileChooser();
    }

    private void openFileChooser() {

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);

        intent.addCategory(Intent.CATEGORY_OPENABLE);

        intent.setType("*/*");

        intent.putExtra(
                Intent.EXTRA_ALLOW_MULTIPLE,
                false
        );

        fileChooserLauncher.launch(intent);
    }


}