package com.firrael.tracker.openCV;

import android.content.Intent;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import com.firrael.tracker.App;
import com.firrael.tracker.AttachFragment;
import com.firrael.tracker.R;
import com.firrael.tracker.tesseract.Tesseract;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.DriveResourceClient;
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.MetadataBuffer;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.drive.query.Filters;
import com.google.android.gms.drive.query.Query;
import com.google.android.gms.drive.query.SearchableField;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.features2d.MSER;
import org.opencv.imgproc.Imgproc;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

import static org.opencv.android.Utils.matToBitmap;

/**
 * Created by railag on 04.01.2018.
 */

public class OpenCVActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private final static String TAG = OpenCVActivity.class.getSimpleName();

    public final static String KEY_SCAN_RESULTS = "scanResults";

    private final static Scalar CONTOUR_COLOR = Scalar.all(100);

    private Tesseract mTesseract;
    private MSER mDetector;
    private FocusCameraView mOpenCVCameraView;
    private Mat mGrey, mRgba, mIntermediateMat;
    private DriveResourceClient mDriveResourceClient;
    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    mOpenCVCameraView.enableView();
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_opencv);

        Window window = getWindow();

        //FULLSCREEN MODE
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //transparent toolbar & status bar
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        getSupportActionBar().hide();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        // C++: static Ptr_MSER create(int _delta = 5, int _min_area = 60, int _max_area = 14400, double _max_variation = 0.25, double _min_diversity = .2, int _max_evolution = 200, double _area_threshold = 1.01, double _min_margin = 0.003, int _edge_blur_size = 5)

        mTesseract = new Tesseract(this);
        mDetector = MSER.create();
    // TODO use all features    mDetector = MSER.create(5, 60, 14400, 0.25,
    //            0.2, 200, 1.01, 0.003, 5);
    //    mDetector.setMinArea(60);
    //    mDetector.setMaxArea(14400);
    //    mDetector.setDelta(5);
    // TODO ???    mDetector.setPass2Only();

        mDriveResourceClient = App.getDrive();

        mOpenCVCameraView = findViewById(R.id.javaCameraView);
        mOpenCVCameraView.setOnClickListener(v1 -> {
            if (mTesseract.isAvailable()) {
                detectTextAsync();
            } else {
                Toast.makeText(this, "Wait till previous recognition is finished", Toast.LENGTH_SHORT).show();
            }
        });

        mOpenCVCameraView.setVisibility(View.VISIBLE);
        mOpenCVCameraView.setCvCameraViewListener(this);
    }

    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        mGrey = inputFrame.gray();
        mRgba = inputFrame.rgba();
        mIntermediateMat = mGrey;

        //    detectRegions();
        return mRgba;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCVCameraView != null)
            mOpenCVCameraView.disableView();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0, this, mLoaderCallback);
        } else {
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCVCameraView != null)
            mOpenCVCameraView.disableView();
    }

    public void onCameraViewStarted(int width, int height) {
        mOpenCVCameraView.setFocusMode(this, 6); // Continuous Picture Mode

        mIntermediateMat = new Mat();
        mGrey = new Mat(height, width, CvType.CV_8UC4);
        mRgba = new Mat(height, width, CvType.CV_8UC4);
    }

    public void detectTextAsync() {
        List<Bitmap> regions = detectRegions();

        uploadBitmapsToGoogleDrive(regions);

        Observable<List<String>> observable = mTesseract.getOCRResult(regions);

        observable
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onSuccess, this::onError);
    }

    private void onError(Throwable throwable) {
        throwable.printStackTrace();
    }

    private void onSuccess(List<String> s) {
        Log.i(TAG, "Results: " + s);
        Intent data = new Intent();
        ArrayList<String> results = new ArrayList<>(s);
        data.putStringArrayListExtra(KEY_SCAN_RESULTS, results);
        setResult(RESULT_OK, data);
        finish();
    }

    private List<Bitmap> detectRegions() {
        MatOfKeyPoint keypoint = new MatOfKeyPoint();
        List<KeyPoint> listpoint;
        KeyPoint kpoint;
        Mat mask = Mat.zeros(mGrey.size(), CvType.CV_8UC1);
        int rectanx1;
        int rectany1;
        int rectanx2;
        int rectany2;
        int imgsize = mGrey.height() * mGrey.width();
        Scalar zeros = new Scalar(0, 0, 0);

        List<MatOfPoint> contour2 = new ArrayList<>();
        Mat kernel = new Mat(1, 50, CvType.CV_8UC1, Scalar.all(255));
        Mat morbyte = new Mat();
        Mat hierarchy = new Mat();

        Rect rectan3;
        //

        mDetector.detect(mGrey, keypoint);
        listpoint = keypoint.toList();

        for (int i = 0; i < listpoint.size(); i++) {
            kpoint = listpoint.get(i);
            rectanx1 = (int) (kpoint.pt.x - 0.5 * kpoint.size);
            rectany1 = (int) (kpoint.pt.y - 0.5 * kpoint.size);
            rectanx2 = (int) (kpoint.size);
            rectany2 = (int) (kpoint.size);
            if (rectanx1 <= 0)
                rectanx1 = 1;
            if (rectany1 <= 0)
                rectany1 = 1;
            if ((rectanx1 + rectanx2) > mGrey.width())
                rectanx2 = mGrey.width() - rectanx1;
            if ((rectany1 + rectany2) > mGrey.height())
                rectany2 = mGrey.height() - rectany1;
            Rect rectant = new Rect(rectanx1, rectany1, rectanx2, rectany2);
            try {
                Mat roi = new Mat(mask, rectant);
                roi.setTo(CONTOUR_COLOR);
            } catch (Exception ex) {
                Log.d(TAG, "mat roi error " + ex.getMessage());
            }
        }

        Imgproc.morphologyEx(mask, morbyte, Imgproc.MORPH_DILATE, kernel); // dilate filter

        Imgproc.findContours(morbyte, contour2, hierarchy,
                Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_NONE);

        List<Bitmap> bitmapsToRecognize = new ArrayList<>();

        for (int ind = 0; ind < contour2.size(); ind++) {
            rectan3 = Imgproc.boundingRect(contour2.get(ind));
            Imgproc.rectangle(mGrey, rectan3.br(), rectan3.tl(),
                    CONTOUR_COLOR);
            Bitmap bmp = null;
            try {
                Mat croppedPart;
                croppedPart = mIntermediateMat.submat(rectan3); // gain mat from rectan3?
                bmp = Bitmap.createBitmap(croppedPart.width(), croppedPart.height(), Bitmap.Config.ARGB_8888);
                matToBitmap(croppedPart, bmp);
            } catch (Exception e) {
                Log.d(TAG, "cropped part data error " + e.getMessage());
            }
            if (bmp != null) {
                //    recognize(bmp);
                bitmapsToRecognize.add(bmp);
                //bmp.recycle();
                //Log.i(TAG, "Result: " + result);
            }
        }

        Bitmap b = Bitmap.createBitmap(mGrey.width(), mGrey.height(), Bitmap.Config.ARGB_8888);
        matToBitmap(mGrey, b);
        bitmapsToRecognize.add(b);

        return bitmapsToRecognize;
    }

    @Override
    public void onCameraViewStopped() {
        Log.i(TAG, "onCameraViewStopped called");
    }

    public void uploadBitmapsToGoogleDrive(List<Bitmap> regions) {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        final String folderName = timeStamp + " openCV";

        Task<DriveFolder> folderTask = createGoogleDriveFolder(folderName);
        Tasks.whenAll(folderTask).continueWith((Continuation<Void, Void>) task -> {
            DriveFolder parentFolder = folderTask.getResult();

            for (int i = 0; i < regions.size(); i++) {
                addGoogleDriveImage(regions.get(i), "region #" + i, folderName);
            }

            return null;
        });
    }

    private void addGoogleDriveImage(Bitmap image, String name, String folderName) {
        final Task<DriveContents> createContentsTask = mDriveResourceClient.createContents();
        Query query = new Query.Builder()
                .addFilter(Filters.eq(SearchableField.TITLE, folderName))
                .build();
        Task<MetadataBuffer> folders = mDriveResourceClient.query(query);
        Tasks.whenAll(folders, createContentsTask).continueWithTask(task -> {
            Metadata folderMetadata = null;
            MetadataBuffer metadataBuffer = folders.getResult();
            for (int i = 0; i < metadataBuffer.getCount(); i++) {
                Metadata metadata = metadataBuffer.get(i);
                if (metadata.getTitle().equalsIgnoreCase(folderName)) {
                    folderMetadata = metadata;
                    break;
                }
            }

            DriveFolder folder = folderMetadata.getDriveId().asDriveFolder();

            DriveContents contents = createContentsTask.getResult();
            OutputStream outputStream = contents.getOutputStream();
            image.compress(Bitmap.CompressFormat.PNG, 100, outputStream);

            MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
                    .setTitle(name)
                    .setMimeType("image/jpeg")
                    .build();

            // TODO    metadataBuffer.release();

            return mDriveResourceClient.createFile(folder, changeSet, contents);
        })
                .addOnSuccessListener(this,
                        driveFile -> Log.i(TAG, "Upload finished " + name))
                .addOnFailureListener(this, e -> {
                    Log.e(TAG, "Unable to create file", e);
                });
    }


    private Task<DriveFolder> createGoogleDriveFolder(String name) {
        final Task<DriveFolder> rootFolderTask = mDriveResourceClient.getRootFolder();
        rootFolderTask.continueWithTask(new Continuation<DriveFolder, Task<DriveFolder>>() {
            @Override
            public Task<DriveFolder> then(@NonNull Task<DriveFolder> task) throws Exception {
                DriveFolder parentFolder = task.getResult();
                MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
                        .setTitle(name)
                        .setMimeType(DriveFolder.MIME_TYPE)
                        .setStarred(true)
                        .build();
                return mDriveResourceClient.createFolder(parentFolder, changeSet);
            }
        });
        return rootFolderTask;
    }
}