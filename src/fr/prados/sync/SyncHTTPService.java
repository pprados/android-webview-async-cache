package fr.prados.sync;

import java.util.ArrayList;

import android.accounts.Account;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ContentProvider;
import android.content.ContentProviderClient;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SyncResult;
import android.database.Cursor;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.webkit.HttpAuthHandler;
import android.webkit.SslErrorHandler;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class SyncHTTPService extends AbstractSyncService
{
	// Set to log debug
	private static final boolean D=false;
	// Set to log info
	private static final boolean I=true;
	
	// Handler to run loadUrl() in UI thread
	private Handler mHandler=new Handler();
	// The hidden webview to load urls.
	private WebView mWebView;
	// Flag on synchronization ?
	private volatile boolean mOnSync;
	// Lock objet to synchronize onPerformSync and UI thread.
	private Object mLock=new Object();

	// Dummy content provider for synchronize the WebKit cache.
	public static class CacheHTTPContentProvider extends ContentProvider
	{
		@Override
		public boolean onCreate()
		{
			return true;
		}

		@Override
		public int delete(Uri uri, String s, String[] as)
		{
			throw new UnsupportedOperationException("Not supported by this provider");
		}

		@Override
		public String getType(Uri uri)
		{
			throw new UnsupportedOperationException("Not supported by this provider");
		}

		@Override
		public Uri insert(Uri uri, ContentValues contentvalues)
		{
			throw new UnsupportedOperationException("Not supported by this provider");
		}

		@Override
		public Cursor query(Uri uri, String[] as, String s, String[] as1, String s1)
		{
			throw new UnsupportedOperationException("Not supported by this provider");
		}

		@Override
		public int update(Uri uri, ContentValues contentvalues, String s, String[] as)
		{
			throw new UnsupportedOperationException("Not supported by this provider");
		}

	}

	/**
	 * A sync service for a specific account.
	 */
	public SyncHTTPService()
	{
	}

	/**
	 * Synchronize the content provider.
	 * Call back invoked by the Android framework, when it's time to synchronize the content provider.
	 * Ask to an hidden WebView to load each registered URL.
	 *
	 * @param context The context.
	 * @param account The account.
	 * @param extras Some more informations.
	 * @param authority The authority of the content provider.
	 * @param provider The content provider client.
	 * @param result The message for result.
	 *
	 * @see AbstractThreadedSyncAdapter.onPerformSync()
	 */
    protected void onPerformSync(
    		Context context,
    		Account account,
    		Bundle extras,
    		String authority,
    		ContentProviderClient provider,
			SyncResult result)
	{
		if (D) Log.d(TAG,"Start HTTP sync");
		if (mOnSync)
		{
			if (D) Log.d(TAG,"*** sync refused.");
			return;
		}
		mOnSync=true;

		// Refresh Webkit cache
		final ArrayList<String> urls=new ArrayList<String>();
		// First, extract all URL to load in an array.
		SharedPreferences preferences=context.getSharedPreferences("HTTPCache", Context.MODE_PRIVATE);
    	int i=0;
    	String u=null;
		String key=null;
    	do
    	{
    		key="url."+i;
    		u=preferences.getString(key, null);
    		if (u==null) break;
    		urls.add(u);
    		++i;
    	} while (u!=null);

    	// Because the current thread is not the UI thread, post a message in the UI thread
    	// to load the first URL.
    	if (urls.size()==0)
    	{
    		mOnSync=false;
    		return;
    	}
		mHandler.post(new Runnable()
		{
			// The current index of Url to manage.
			private int mState=0;
			// The current url
			private String mUrl;

			@Override
			public void run()
			{
				// Must be call in UI thread
				if (mWebView==null)
				{
					// Create a WebView disconnected from a context.
					mWebView=new WebView(SyncHTTPService.this);
					// Init the WebView to manage the HTML5 cache.
					initWebViewForHTML5Cache(SyncHTTPService.this,mWebView);
					// Update only if is necessary
					mWebView.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
					mWebView.setWebViewClient(new WebViewClient()
					{

						@Override
						@Deprecated
						public void onTooManyRedirects(WebView view, Message cancelMsg, Message continueMsg)
						{
							nextUrl();
						}
						@Override
						public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error)
						{
							nextUrl();
						}
						@Override
						public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler, String host,
								String realm)
						{
							nextUrl();
						}
						@Override
						public void onReceivedLoginRequest(WebView view, String realm, String account, String args)
						{
							nextUrl();
						}
						@Override
						public void onReceivedError(WebView view, int errorCode, String description, String failingUrl)
						{
							nextUrl();
						}
						@Override
						public void onPageFinished(WebView view, String url)
						{
							nextUrl();
						}
						// load the next url
						@TargetApi(Build.VERSION_CODES.HONEYCOMB)
						private void nextUrl()
						{
							// Notify this page is loaded
							if (I) Log.i(TAG,mUrl+" is updated");
							getContentResolver().notifyChange(Uri.parse(mUrl), null);
							// Is it the last URL ?
							if (mState>=urls.size())
							{
								if (D) Log.d(TAG,"Finish the last URL. onPause() the WebKit.");
								// Invoke onPause() of the current URL (stop flash, video, etc.)
								if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.HONEYCOMB)
									mWebView.onPause();
								else
								{
									try
									{
										Class.forName("android.webkit.WebView").getMethod("onPause", (Class[]) null).invoke(mWebView, (Object[]) null);
									}
									catch (Exception e)
									{
										// IGNORE
										Log.e(TAG,"pause webview in sync.",e);
									}
								}
								// All is done
								mWebView=null; // Garbage the WebView
								// It's time to notify the onPerformSync method.
								synchronized (mLock)
								{
									if (D) Log.d(TAG,"*** sync notify");
									mLock.notify(); // Unlock the onPerformSync
								}
							}
							else
							{
								// Else, continue the Finite state machine with the next URL.
								mUrl=urls.get(mState++);
								if (D) Log.d(TAG,"Preload "+mUrl);
								mWebView.loadUrl(mUrl);
							}

						}
					});
				}
				// Take the next URL
				mUrl=urls.get(mState++);
				if (D) Log.d(TAG,"Preload "+mUrl);
				// Wait the onPageFinished
				// and load the Url in the WebView
				mWebView.loadUrl(mUrl);
			}
		});
		// Wait to load all URL
		synchronized (mLock)
		{
			try
			{
				if (D) Log.d(TAG,"Sync is waiting...");
				mLock.wait(1000*60*2L);
			}
			catch (InterruptedException e)
			{
				// Ignore
			}
		}
		try
		{
			Thread.sleep(10*1000); // Last 10s
		}
		catch (InterruptedException e)
		{
			// Ignore
		}
		if (D) Log.d(TAG,"HTTP sync finished");
		mOnSync=false; // Signal the sync process is finished
		// Inform the framework
		result.delayUntil=60*60; // sec
		result.stats.numUpdates=urls.size();
	}
    
    /**
     * Initialize a WebView to be compatible with HTML5 manifest.
     *
     * @param context The context.
     * @param webView The web view.
     */
    @SuppressLint("SetJavaScriptEnabled")
	@SuppressWarnings("deprecation")
	private static void initWebViewForHTML5Cache(Context context,WebView webView)
    {
		final WebSettings settings = webView.getSettings();
		settings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
		settings.setJavaScriptEnabled(true); // Accept javascript to synchronize data
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

	/**
     * Register a new URL to pre-load.
     *
     * @param context The context
     * @param url The URL to cache.
     */
    public static void registerURL(Context context,String url)
    {
    	SharedPreferences preferences=context.getSharedPreferences("HTTPCache", Context.MODE_PRIVATE);
    	int i=0;
    	String u=null;
		String key=null;
    	do
    	{
    		key="url."+i;
    		u=preferences.getString(key, null);
    		if (u==null) break;
    		if (url.equals(u))
    			return;
    		++i;
    	} while (u!=null);
		preferences.edit().putString(key, url).commit();
    }
    /**
     * Unregister a URL to cache.
     *
     * @param context The context.
     * @param url The URL to remove from cache.
     */
    public static void unregisterURL(Context context,String url)
    {
    	SharedPreferences preferences=context.getSharedPreferences("HTTPCache", Context.MODE_PRIVATE);
    	int i=0;
    	String u=null;
		String key=null;
		boolean removed=false;
		Editor edit = preferences.edit();
    	do
    	{
    		key="url."+i;
    		u=preferences.getString(key, null);
    		if (u==null) break;
    		if (removed)
    			edit.putString("url."+(i-1),u);
    		if (url.equals(u))
    		{
    			edit.remove(key);
    			removed=true;
    		}
    		++i;
    	} while (u!=null);
    	edit.remove("url."+(i-1));
    	edit.commit();
    }
}
