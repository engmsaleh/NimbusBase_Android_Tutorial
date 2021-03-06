package com.nimbusbase.nimbusbase_android_tutorial;

import android.app.ActionBar;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.nimbusbase.nimbusbase.Base;
import com.nimbusbase.nimbusbase.Server;
import com.nimbusbase.nimbusbase.promise.Callback;
import com.nimbusbase.nimbusbase.promise.NMBError;
import com.nimbusbase.nimbusbase.promise.Promise;
import com.nimbusbase.nimbusbase.promise.Response;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Will on 11/7/14.
 */
public class IndexFragment extends PreferenceFragment {

    private static final Map<Server.AuthState, String>
            sAuthStateText = new HashMap<Server.AuthState, String>(4) {{
        put(Server.AuthState.In, "In");
        put(Server.AuthState.Out, "Out");
        put(Server.AuthState.SigningIn, "Signing in");
        put(Server.AuthState.SigningOut, "Signing out");
    }};
    private static final Map<Boolean, String>
            sInitStateText = new HashMap<Boolean, String>(2) {{
        put(true, "Initialized");
        put(false, "Initializing");
    }};

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);

        final Base
                base = getBase();
        bindEvents(base);
        initiatePreferenceScreen(base, R.xml.fragment_index);
    }

    @Override
    public void onDestroy() {
        final Base
                base = getBase();
        unbindEvents(base);
        super.onDestroy();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final ActionBar
                actionBar = getActivity().getActionBar();
        if (actionBar != null)
            actionBar.setTitle(R.string.app_name);
   }

    protected void bindEvents(Base base) {
        final Server[]
                servers = base.getServers();
        for (int index = 0; index < servers.length; index ++) {
            final Server
                    server = servers[index];
            final PropertyChangeSupport
                    support = server.propertyChangeSupport;
            final int
                    innerIndex = index;
            support.addPropertyChangeListener(
                    Server.Property.authState,
                    new PropertyChangeListener() {
                        @Override
                        public void propertyChange(PropertyChangeEvent event) {
                            final Server
                                    innerServer = (Server) event.getSource();
                            onServerStateChange(innerServer, innerIndex);
                        }
                    }
            );
            support.addPropertyChangeListener(
                    Server.Property.isInitialized,
                    new PropertyChangeListener() {
                        @Override
                        public void propertyChange(PropertyChangeEvent event) {
                            final Server
                                    innerServer = (Server) event.getSource();
                            onServerStateChange(innerServer, innerIndex);
                        }
                    }
            );
        }
    }

    protected void unbindEvents(Base base) {
        final Server[]
                servers = base.getServers();
        for (final Server server : servers) {
            final PropertyChangeSupport
                    support = server.propertyChangeSupport;
            for (final PropertyChangeListener listener : support.getPropertyChangeListeners()) {
                support.removePropertyChangeListener(listener);
            }
        }
    }

    protected PreferenceScreen initiatePreferenceScreen(Base base, int preferencesResID) {
        addPreferencesFromResource(preferencesResID);
        final PreferenceScreen
                preferenceScreen = getPreferenceScreen();

        final PreferenceCategory
                serverCate = getServerCategory(preferenceScreen);
        serverCate.setOrderingAsAdded(true);

        final Server[]
                servers =  base.getServers();
        for (int index = 0; index < servers.length; index++) {
            final Server
                    server = servers[index];

            final ListItemServer
                    item = new ListItemServer(getActivity(), server);

            item.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    onServerItemStateChange((ListItemServer) preference, (Boolean) newValue);
                    return false;
                }
            });
            item.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    onServerItemClick((ListItemServer) preference);
                    return true;
                }
            });
            serverCate.addPreference(item);

            onServerStateChange(server, index);
        }

        final PreferenceCategory
                databaseCate = getDatabaseCategory(preferenceScreen);
        final PreferenceScreen
                playgroundItem = (PreferenceScreen) databaseCate.findPreference(getString(R.string.item_playground));
        playgroundItem.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                return onPlaygroundItemClick(preference);
            }
        });

        return preferenceScreen;
    }

    protected void onServerItemClick(ListItemServer item) {
        final Server
                server = item.getServer();
        if (server.isSynchronizing()) {
            server.getRunningSync().cancel();
        }
        else if (server.canSynchronize()) {
            final int
                    index = Arrays.asList(getBase().getServers()).indexOf(server);
            startSyncOnServer(server, index);
        }
    }

    protected void onServerItemStateChange(ListItemServer item, Boolean newValue) {
        final Server
                server = item.getServer();
        final Server.AuthState
                authState = server.getAuthState();
        if (newValue && Server.AuthState.Out == authState) {
            server.authorize(getActivity());
        }
        else if (!newValue && Server.AuthState.In == authState) {
            server.signOut();
        }
    }

    protected void onServerStateChange(Server server, int index) {
        final Server.AuthState
                authState = server.getAuthState();
        final boolean
                initialized = server.isInitialized();
        final  boolean
                syncing = server.isSynchronizing();

        final ListItemServer
                item = (ListItemServer) getServerCategory(getPreferenceScreen()).getPreference(index);
        if (item == null) return;

        if (Server.AuthState.In == authState) {
            item.setChecked(true);
        }
        else if (Server.AuthState.Out == authState) {
            item.setChecked(false);
        }

        if (!syncing) {
            if (Server.AuthState.In == authState) {
                item.setSummary(sInitStateText.get(initialized));
            }
            else {
                item.setSummary(sAuthStateText.get(authState));
            }
        }
    }

    protected void startSyncOnServer(final Server server, final int index) {
        final Promise
                promise = server.synchronize(null);
        promise
                .onProgress(new Callback.ProgressListener() {
                    @Override
                    public void onProgress(double v) {
                        onServerSyncProgress(server, index, (float) v);
                    }
                })
                .onAlways(new Callback.AlwaysListener() {
                    @Override
                    public void onAlways(Response response) {
                        onServerSyncEnd(server, index, response);
                    }
                });

        final ListItemServer
                item = (ListItemServer) getServerCategory(getPreferenceScreen()).getPreference(index);
        if (item != null)
            item.setSummary(summaryForSyncProgress(0.0f));
    }

    protected void onServerSyncEnd(Server server, int index, Response response) {
        if (!response.isSuccess()) {
            final NMBError
                    error = response.error;
            if (error != null)
                Toast.makeText(getActivity(), error.toString(), Toast.LENGTH_LONG).show();
        }

        onServerStateChange(server, index);     // Reset summary
    }

    protected void onServerSyncProgress(Server server, int index, float progress) {
        final ListItemServer
                item = (ListItemServer) getServerCategory(getPreferenceScreen()).getPreference(index);
        if (item != null)
            item.setSummary(summaryForSyncProgress(progress));
    }

    protected boolean onPlaygroundItemClick(Preference item) {
        final PGFragmentTable
                fragment = PGFragmentTable.newInstance(MDLUser.ENTITY_NAME);
        getFragmentManager()
                .beginTransaction()
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .replace(android.R.id.content, fragment)
                .addToBackStack(null)
                .commit();
        return true;
    }

    private PreferenceCategory getServerCategory(PreferenceScreen preferenceScreen) {
        return (PreferenceCategory) preferenceScreen.findPreference(getString(R.string.group_servers));
    }

    private PreferenceCategory getDatabaseCategory(PreferenceScreen preferenceScreen) {
        return (PreferenceCategory) preferenceScreen.findPreference(getString(R.string.group_database));
    }

    private static String summaryForSyncProgress(float progress) {
        return String.format("Synchronizing %3.0f%%", progress * 100);
    }

    private static Base getBase() {
        return Singleton.base();
    }
}
