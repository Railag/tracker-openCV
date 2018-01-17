package com.firrael.tracker;

import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatDrawableManager;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import com.firrael.tracker.openCV.OpenCVActivity;
import com.firrael.tracker.realm.RealmDB;
import com.firrael.tracker.realm.TaskModel;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveClient;
import com.google.android.gms.drive.DriveResourceClient;
import com.google.android.gms.tasks.Task;
import com.wang.avi.AVLoadingIndicatorView;

import org.opencv.android.OpenCVLoader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

import io.realm.Realm;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final String TAG_MAIN = "mainTag";

    private static final int REQUEST_GOOGLE_SERVICES_AVAILABILITY = 100;
    private static final int REQUEST_GOOGLE_SIGN_IN = 101;

    private Toolbar toolbar;
    private AVLoadingIndicatorView loading;
    private TextView toolbarTitle;

    private GoogleSignInClient mGoogleSignInClient;
    private DriveClient mDriveClient;
    private DriveResourceClient mDriveResourceClient;

    private FloatingActionButton mFab;

    private Fragment currentFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        toolbar = findViewById(R.id.toolbar);

        loading = findViewById(R.id.loading);

        toolbarTitle = findViewById(R.id.toolbarTitle);

        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        App.setMainActivity(this);

        toolbar.setNavigationOnClickListener(v -> onBackPressed());


        mFab = findViewById(R.id.fab);

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        initOpenCV();

        toSplash();

        mGoogleSignInClient = buildGoogleSignInClient();

        try {
            handleSignInResult(mGoogleSignInClient.silentSignIn());
        } catch (Exception e) {
            e.printStackTrace();
            Intent signInIntent = mGoogleSignInClient.getSignInIntent();
            startActivityForResult(signInIntent, REQUEST_GOOGLE_SIGN_IN);
        }
    }

    private GoogleSignInClient buildGoogleSignInClient() {
        GoogleSignInOptions signInOptions =
                new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestScopes(Drive.SCOPE_FILE)
                        .build();
        return GoogleSignIn.getClient(this, signInOptions);
    }

    private void initOpenCV() {
        boolean initialized = OpenCVLoader.initDebug();
        if (initialized) {
            Log.i(TAG, "OpenCV initialized successfully.");
        } else {
            Log.i(TAG, "Error during OpenCV initialization.");
        }
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void setTitle(CharSequence title) {
        if (toolbarTitle != null) {
            toolbarTitle.setText(title);
        }
    }

    public void startLoading() {
        loading.setVisibility(View.VISIBLE);
    }

    public void stopLoading() {
        loading.setVisibility(View.GONE);
    }

    private <T extends Fragment> void setFragment(final T fragment) {
        runOnUiThread(() -> {
            currentFragment = fragment;

            final FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();

            // TODO custom transaction animations
            fragmentTransaction.addToBackStack(fragment.getClass().getSimpleName());
            fragmentTransaction.replace(R.id.mainFragment, fragment, TAG_MAIN);
            fragmentTransaction.commitAllowingStateLoss();

        });
    }

    public void setCurrentFragment(Fragment fragment) {
        this.currentFragment = fragment;
    }

    public void showToolbar() {
        toolbar.setVisibility(View.VISIBLE);
    }

    public void hideToolbar() {
        toolbar.setVisibility(View.GONE);
    }

    public void transparentStatusBar() {
        Window window = getWindow();

        window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
    }

    public void toSplash() {
        setFragment(SplashFragment.newInstance());
    }

    public void toOpenCV() {
        Intent intent = new Intent(this, OpenCVActivity.class);
        startActivity(intent);
    }

    public void toNewTask() {
        setFragment(NewTaskFragment.newInstance());
    }

    public void toAttach(String taskName) {
        setFragment(AttachFragment.newInstance(taskName));
    }

    public void toEditTask(TaskModel task) {
        setFragment(EditTaskFragment.newInstance(task));
    }

    public void toLanding() {
        setFragment(LandingTaskFragment.newInstance());
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_opencv) {
            toOpenCV();
        } else if (id == R.id.nav_add_task) {
            toNewTask();
        }  else if (id == R.id.nav_manage) {

        } else if (id == R.id.nav_share) {

        } else if (id == R.id.nav_send) {

        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkGooglePlayServices();
    }

    private void checkGooglePlayServices() {
        GoogleApiAvailability availability = GoogleApiAvailability.getInstance();
        int result = availability.isGooglePlayServicesAvailable(this);
        switch (result) {
            case ConnectionResult.SUCCESS:
                break;
            default:
                availability.getErrorDialog(this, result, REQUEST_GOOGLE_SERVICES_AVAILABILITY).show();
        }
    }

    /* private boolean isFolderExists(String folderName) {

         DriveId folderId = DriveId.decodeFromString(folderName);
         DriveFolder folder = Drive.DriveApi.getFolder(mGoogleApiClient, folderId);
         folder.getMetadata(mGoogleApiClient).setResultCallback(metadataRetrievedCallback);

     }

     final private ResultCallback<DriveResource.MetadataResult> metadataRetrievedCallback = new
             ResultCallback<DriveResource.MetadataResult>() {
                 @Override
                 public void onResult(DriveResource.MetadataResult result) {
                     if (!result.getStatus().isSuccess()) {
                         Log.v(TAG, "Problem while trying to fetch metadata.");
                         return;
                     }

                     Metadata metadata = result.getMetadata();
                     if(metadata.isTrashed()){
                         Log.v(TAG, "Folder is trashed");
                     }else{
                         Log.v(TAG, "Folder is not trashed");
                     }

                 }
             };
 */

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQUEST_GOOGLE_SIGN_IN:
                Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
                handleSignInResult(task);
                break;
        }
    }

    private void handleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);

            // Signed in successfully, show authenticated UI.
            Log.i(TAG, "Sign in success");
            // Build a drive client.
            mDriveClient = Drive.getDriveClient(getApplicationContext(), account);
            // Build a drive resource client.
            mDriveResourceClient =
                    Drive.getDriveResourceClient(getApplicationContext(), account);

            App.setDrive(mDriveResourceClient);

        } catch (ApiException e) {
            // The ApiException status code indicates the detailed failure reason.
            // Please refer to the GoogleSignInStatusCodes class reference for more information.
            Log.w(TAG, "signInResult:failed code=" + e.getStatusCode());
        }
    }

    public final static int FAB_NEW = 0;
    public final static int FAB_NEXT = 1;
    public final static int FAB_DONE = 2;
    public void setupFab(View.OnClickListener listener, int fabState) {
        if (mFab != null) {
            showFab();
            mFab.setOnClickListener(listener);


            int fabDrawableId = R.drawable.ic_menu_send;
            switch (fabState) {
                case FAB_NEW:
                    fabDrawableId = R.drawable.ic_add_black_24dp;
                    break;
                case FAB_NEXT:
                    fabDrawableId = R.drawable.ic_forward_black_24dp;
                    break;
                case FAB_DONE:
                    fabDrawableId = R.drawable.ic_done_black_24dp;
                    break;
            }
            mFab.setImageDrawable(ContextCompat.getDrawable(this, fabDrawableId));
        }
    }

    private void showFab() {
        if (mFab != null) {
            mFab.setVisibility(View.VISIBLE);
        }
    }

    public void hideFab() {
        if (mFab != null) {
            mFab.setVisibility(View.GONE);
        }
    }
}
