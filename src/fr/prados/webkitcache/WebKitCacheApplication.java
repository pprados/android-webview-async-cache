package fr.prados.webkitcache;

import fr.prados.sync.SyncHTTPService;
import android.app.Application;

public class WebKitCacheApplication extends Application
{
	/**
	 * Initialize the application.
	 */
	@Override
	public void onCreate()
	{
		super.onCreate();
		// Register an URL for the cache
		SyncHTTPService.registerURL(this,MainActivity.URL_HOME);
		// and initialize the first synchronization
		SyncHTTPTools.initializeHTTPSyncService(this);
	}
}
