package fr.prados.sync;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.NetworkErrorException;
import android.annotation.TargetApi;
import android.app.Service;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

/**
 * Define a Service that returns an IBinder for the sync adapter class, allowing
 * the sync adapter framework to call onPerformSync().
 */
public abstract class AbstractSyncService extends Service
{
	public static final String TAG="SyncService";
	
    // Sync adapter
	private SyncAdapter mSyncAdapter;
	// Empty authenticator
	private AbstractAccountAuthenticator mAuthenticator;
	
	// Dummy authenticator
	private static class AccountAuthenticator extends AbstractAccountAuthenticator
	{
		AccountAuthenticator(Context context)
		{
			super(context.getApplicationContext());
		}
		@Override
		public Bundle editProperties(AccountAuthenticatorResponse accountAuthenticatorResponse, String s)
		{
			throw new UnsupportedOperationException();
		}

		@Override
		public Bundle addAccount(AccountAuthenticatorResponse accountAuthenticatorResponse, String s, String s2,
				String[] strings, Bundle bundle) throws NetworkErrorException
		{
			return null;
		}

		@Override
		public Bundle confirmCredentials(AccountAuthenticatorResponse accountAuthenticatorResponse, Account account,
				Bundle bundle) throws NetworkErrorException
		{
			return null;
		}

		@Override
		public Bundle getAuthToken(AccountAuthenticatorResponse accountAuthenticatorResponse, Account account,
				String s, Bundle bundle) throws NetworkErrorException
		{
			throw new UnsupportedOperationException();
		}

		@Override
		public String getAuthTokenLabel(String s)
		{
			throw new UnsupportedOperationException();
		}

		@Override
		public Bundle updateCredentials(AccountAuthenticatorResponse accountAuthenticatorResponse, Account account,
				String s, Bundle bundle) throws NetworkErrorException
		{
			throw new UnsupportedOperationException();
		}

		@Override
		public Bundle hasFeatures(AccountAuthenticatorResponse accountAuthenticatorResponse, Account account,
				String[] strings) throws NetworkErrorException
		{
			throw new UnsupportedOperationException();
		}
	};

	// Wrapper sync adapter
	private class SyncAdapter extends AbstractThreadedSyncAdapter
	{
		/**
		 * Set up the sync adapter
		 */
		SyncAdapter(Context context, boolean autoInitialize)
		{
			super(context, autoInitialize);
		}

		/**
		 * Set up the sync adapter. This form of the constructor maintains
		 * compatibility with Android 3.0 and later platform versions
		 */
		@TargetApi(Build.VERSION_CODES.HONEYCOMB)
		public SyncAdapter(Context context, boolean autoInitialize, boolean allowParallelSyncs)
		{
			super(context, autoInitialize, allowParallelSyncs);
		}

		@Override
		public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider,
				SyncResult result)
		{
			AbstractSyncService.this.onPerformSync(getContext(),account, extras, authority, provider, result);
		}
	}
	
	protected AbstractSyncService()
	{
	}
	/*
	 * Instantiate the sync adapter object.
	 */
	@Override
	public void onCreate()
	{
		mAuthenticator=new AccountAuthenticator(this);
		mSyncAdapter=(Build.VERSION.SDK_INT>=Build.VERSION_CODES.HONEYCOMB) 
				? new SyncAdapter(getApplicationContext(), true,true)
				: new SyncAdapter(getApplicationContext(), true);
	}

	/**
	 * Return an object that allows the system to invoke the sync adapter.
	 * 
	 */
	@Override
	public IBinder onBind(Intent intent)
	{
		final String action = intent.getAction();
		if (action.equals("android.accounts.AccountAuthenticator"))
		{
			return mAuthenticator.getIBinder();
		}
		else if (action.equals("android.content.SyncAdapter"))
		{
			return mSyncAdapter.getSyncAdapterBinder();
		}
		else
			return null;
	}

	/**
	 * Helper method to trigger an immediate sync ("refresh").
	 * 
	 * @param contentAuthority The content authority.
	 * @param account The account.
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


	abstract protected void onPerformSync(Context context,Account account, Bundle extras, String authority, ContentProviderClient provider,
			SyncResult result);
	
}