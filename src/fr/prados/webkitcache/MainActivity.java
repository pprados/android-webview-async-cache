package fr.prados.webkitcache;

import static fr.prados.webkitcache.SyncHTTPTools.ACCOUNT_NAME;
import static fr.prados.webkitcache.SyncHTTPTools.ACCOUNT_TYPE;
import static fr.prados.webkitcache.SyncHTTPTools.CONTENT_AUTHORITY;
import android.accounts.Account;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

// En première étape, il est possible de récupérer un zip avec tous les fichiers, et les publier via le contentprovider

// http://alex.tapmania.org/2010/11/html5-cache-android-webview.html
public class MainActivity extends Activity
{
	public static final String TAG="UseWebKit";
	public static final String URL_HOME="http://www.mobileevolution.be/apps/FitceCongress/android/gsm/home.php";
//	public static final String URL_HOME="https://www.google.fr";

	// The webview
	private WebView mWebView;

	// The URL to present in the webview
	private Uri mHome;
	
	// Observe the HTTP cache
	private final ContentObserver mContentObserver=new ContentObserver((new Handler()))
	{


		@Override
		public void onChange(boolean selfChange)
		{
			super.onChange(selfChange);
			onURLChange(null);
		}

		@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
		@Override
		public void onChange(boolean selfChange, Uri uri)
		{
			super.onChange(selfChange, uri);
			onURLChange(uri);
		}
		
	};
	
	/**
	 * Called by the content observer when the HTTP cache is updated.
	 * It's time to load the url.
	 * 
	 * @param uri The uri present in the Webkit cache.
	 */
	protected void onURLChange(Uri uri)
	{
		if (uri==null) uri=mHome;
		mWebView.loadUrl(uri.toString());
	}
	
	/**
	 * Called when the activity is first created.
	 */
	//@SuppressWarnings("deprecation")
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		mHome=Uri.parse(URL_HOME);

		// Update the Webkit to use the cache
		mWebView = (WebView) findViewById(R.id.webview);
		mWebView.setWebViewClient(new WebViewClient()
		{

			@Override
			public void onReceivedError(WebView view, int errorCode, String description, String failingUrl)
			{
				//super.onReceivedError(view, errorCode, description, failingUrl);
				Log.d(TAG,"error "+errorCode+" "+description+" "+failingUrl);
				switch (errorCode)
				{
					case ERROR_HOST_LOOKUP:
					case ERROR_CONNECT:
						ContentResolver.requestSync(new Account(ACCOUNT_NAME, ACCOUNT_TYPE), CONTENT_AUTHORITY, new Bundle());
						ConnectivityManager cm=((ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE));
						NetworkInfo info=cm.getActiveNetworkInfo();
						final boolean isConnected =	(info!=null) ? info.isConnectedOrConnecting() : false;
						view.loadUrl(isConnected 
							? "file:///android_asset/Waiting.html"
							: "file:///android_asset/NoNetwork.html");
						break;
					default:
						view.loadUrl("file:///android_asset/Error.html");
				}
			}

		});
		initWebViewForHTML5Cache(this,mWebView);

		// Set to load the cache only, because the background service manage the cache.
		mWebView.getSettings().setCacheMode(WebSettings.LOAD_CACHE_ONLY);
		
		mWebView.loadUrl(URL_HOME);
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		// Wait if the cache is updated
		getContentResolver().registerContentObserver(mHome, true,mContentObserver);
	}
	
	@Override
	protected void onPause()
	{
		super.onDestroy();
		getContentResolver().unregisterContentObserver(mContentObserver);
	}

    /**
     * Initialize a WebView to be compatible with HTML5 manifest.
     *
     * @param context The context.
     * @param webView The web view.
     */
	@SuppressWarnings("deprecation")
	private static void initWebViewForHTML5Cache(Context context,WebView webView)
    {
		final WebSettings settings = webView.getSettings();
		settings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
		settings.setJavaScriptEnabled(true);
		settings.setDomStorageEnabled(true);
		settings.setDatabaseEnabled(true);
		settings.setSaveFormData(true);
		settings.setAllowFileAccess(false);
	    // Application cache enabled
		settings.setAppCacheEnabled(true);
		settings.setAppCachePath(context.getCacheDir().getAbsolutePath());
		if (VERSION.SDK_INT < VERSION_CODES.JELLY_BEAN_MR2)
			settings.setAppCacheMaxSize(1024 * 1024 * 8);
    }
	
}
