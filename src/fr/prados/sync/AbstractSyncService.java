package fr.prados.sync;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.NetworkErrorException;
import android.annotation.TargetApi;
import android.app.Service;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;

/**
 * Define a Service that returns an IBinder for the sync adapter class, allowing
 * the sync adapter framework to call onPerformSync() and an authenticator.
 * 
 * @author Philippe PRADOS
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
		/**
		 * {@inheritDoc}
		 */
		@Override
		public Bundle editProperties(AccountAuthenticatorResponse accountAuthenticatorResponse, String s)
		{
			throw new UnsupportedOperationException();
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public Bundle addAccount(AccountAuthenticatorResponse accountAuthenticatorResponse, String s, String s2,
				String[] strings, Bundle bundle) throws NetworkErrorException
		{
			return null;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public Bundle confirmCredentials(AccountAuthenticatorResponse accountAuthenticatorResponse, Account account,
				Bundle bundle) throws NetworkErrorException
		{
			return null;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public Bundle getAuthToken(AccountAuthenticatorResponse accountAuthenticatorResponse, Account account,
				String s, Bundle bundle) throws NetworkErrorException
		{
			throw new UnsupportedOperationException();
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public String getAuthTokenLabel(String s)
		{
			throw new UnsupportedOperationException();
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public Bundle updateCredentials(AccountAuthenticatorResponse accountAuthenticatorResponse, Account account,
				String s, Bundle bundle) throws NetworkErrorException
		{
			throw new UnsupportedOperationException();
		}

		/**
		 * {@inheritDoc}
		 */
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
		 * {@inheritDoc}
		 */
		// Delegate to outer class
		@Override
		public void onSyncCanceled()
		{
			super.onSyncCanceled();
			AbstractSyncService.this.onSyncCanceled();
		}

		/**
		 * {@inheritDoc}
		 */
		// Delegate to outer class
		@TargetApi(Build.VERSION_CODES.HONEYCOMB)
		@Override
		public void onSyncCanceled(Thread thread)
		{
			super.onSyncCanceled(thread);
			AbstractSyncService.this.onSyncCanceled(thread);
		}

		/**
		 * {@inheritDoc}
		 */
		@TargetApi(Build.VERSION_CODES.HONEYCOMB)
		public SyncAdapter(Context context, boolean autoInitialize, boolean allowParallelSyncs)
		{
			super(context, autoInitialize, allowParallelSyncs);
		}

		/**
		 * {@inheritDoc}
		 */
		// Delegate to outer class
		@Override
		public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider,
				SyncResult result)
		{
			AbstractSyncService.this.onPerformSync(getContext(),account, extras, authority, provider, result);
		}
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
	 * Return an object that allows the system to invoke the sync adapter or authenticator.
	 * 
	 * @see android.app.Service#onBind(android.content.Intent)
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
	 * @see {@link android.content.AbstractThreadedSyncAdapter#onSyncCanceled()}
	 */
	protected void onSyncCanceled()
	{
	}

	/**
	 * @see {@link android.content.AbstractThreadedSyncAdapter#onSyncCanceled(Thread)}
	 */
	protected void onSyncCanceled(Thread thread)
	{
	}
	
	/**
	 * @see {@link android.content.AbstractThreadedSyncAdapter#onPerformSync(Account, Bundle, String, ContentProviderClient, SyncResult)}
	 */
	abstract protected void onPerformSync(Context context,Account account, Bundle extras, String authority, ContentProviderClient provider,
			SyncResult result);
	
}