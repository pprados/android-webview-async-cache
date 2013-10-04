package fr.prados.webkitcache;

import java.io.File;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

/**
 * Tool to help the initialization.
 * 
 * @author Philippe PRADOS
 *
 */
public class SyncHTTPTools
{
	public static final String TAG="UseWebKit";
	
    // The authority for the sync adapter's content provider
	public static final String CONTENT_AUTHORITY="fr.prados.webkitcache";
    // An account type, in the form of a domain name
    public static final String ACCOUNT_TYPE = "fr.prados.sync";
    // The account name
    public static final String ACCOUNT_NAME = "sync";
    
    // Sync frequency
	public static final long SYNC_FREQUENCY = 
			BuildConfig.DEBUG 
			? 30 // each 30 seconds in debug mode.
			: 60 * 60 * 12L; // else 12 hours (in seconds)
	
	private static volatile boolean sInitialized=false;
	/**
	 * Bootstrap the synchronization process.
	 * Can be called is each activity.
	 * @param context The context. Not reused.
	 */
	public static void initializeHTTPSyncService(Context context)
	{
		if (!sInitialized)
		{
			sInitialized=true;
			final Account account = initAccount(context);
			// Start the first synchronisation
			final boolean webviewCache=
					new File(context.getCacheDir(),"webviewCache").exists() ||
					new File(context.getCacheDir(),"webviewCacheChromium").exists();
			if (!webviewCache)
				triggerRefreshNow(CONTENT_AUTHORITY,account);
		}
	}
	/**
	 * Create an empty account.
	 * 
	 * @param context The context
	 * @return The account.
	 */
	private static Account initAccount(Context context)
	{
		final AccountManager accountManager = (AccountManager) context.getSystemService(Context.ACCOUNT_SERVICE);
		final Account account=new Account(ACCOUNT_NAME,ACCOUNT_TYPE);
		Account[] accounts=accountManager.getAccountsByType(account.type);
		boolean find=false;
		for (int i=0;i<accounts.length;++i)
		{
			if (accounts[i].name.equals(account.name))
			{
				find=true;
				break;
			}
		}
		if (!find)
		{
			Log.d(TAG,"add new account "+account.name+" of type "+account.type+" for synchronization");
			if (accountManager.addAccountExplicitly(account, null, null))
			{
				// Inform the system that this account supports sync
				ContentResolver.setIsSyncable(account, CONTENT_AUTHORITY, 1);
				// Inform the system that this account is eligible for auto sync when the network is up
				ContentResolver.setSyncAutomatically(account, CONTENT_AUTHORITY, true);
				// Recommend a schedule for automatic synchronization. The system
				// may modify this based on other scheduled syncs and network utilization.
				ContentResolver.addPeriodicSync(account, CONTENT_AUTHORITY, new Bundle(), SYNC_FREQUENCY);
				return account;
			}
		}
		return account;
	}
	
	/**
	 * Helper method to trigger an immediate sync ("refresh").
	 * 
	 * @param acccountName The account name.
	 * @param accountType The account type.
	 * 
	 * <p>
	 * This should only be used when we need to preempt the normal sync
	 * schedule. Typically, this means the user has pressed the "refresh"
	 * button.
	 * 
	 * Note that SYNC_EXTRAS_MANUAL will cause an immediate sync, without any
	 * optimization to preserve battery life. If you know new data is available
	 * (perhaps via a GCM notification), but the user is not actively waiting
	 * for that data, you should omit this flag; this will give the OS
	 * additional freedom in scheduling your sync request.
	 */
	public static void triggerRefreshNow(String contentAuthority,Account account)
	{
		Log.d(TAG,"triggerRefreshNow");
		Bundle b = new Bundle();
		// Disable sync backoff and ignore sync preferences. In other
		// words...perform sync NOW!
		b.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
		b.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
		ContentResolver.requestSync(
			account, // Sync account
			contentAuthority, // Content authority
			b); // Extras
	}
	
}
