package com.regula.documentreader;

import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Toast;

import com.regula.documentreader.api.DocumentReader;
import com.regula.documentreader.api.completions.IDocumentReaderCompletion;
import com.regula.documentreader.api.completions.IDocumentReaderInitCompletion;
import com.regula.documentreader.api.completions.IDocumentReaderPrepareCompletion;
import com.regula.documentreader.api.enums.DocReaderAction;
import com.regula.documentreader.api.enums.eGraphicFieldType;
import com.regula.documentreader.api.enums.eRFID_Password_Type;
import com.regula.documentreader.api.enums.eRPRM_ResultType;
import com.regula.documentreader.api.enums.eVisualFieldType;
import com.regula.documentreader.api.results.DocumentReaderResults;
import com.regula.facesdk.FaceReaderService;
import com.regula.facesdk.enums.eInputFaceType;
import com.regula.facesdk.results.MatchFacesResponse;
import com.regula.facesdk.results.infrastructure.FaceCaptureCallback;
import com.regula.facesdk.results.infrastructure.MatchFaceCallback;
import com.regula.facesdk.structs.Image;
import com.regula.facesdk.structs.MatchFacesRequest;

import java.io.FileNotFoundException;
import java.io.InputStream;

import static android.graphics.BitmapFactory.decodeStream;

public class MainActivity extends AppCompatActivity {

    static final int REQUEST_BROWSE_PICTURE = 11;
    static final int PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 22;
    static final String MY_SHARED_PREFS = "MySharedPrefs";
    static final String DO_RFID = "doRfid";
    static final String RESULTS_TAG = "resultsFragment";

    static final int CAPTURED_FACE = 1;
    static final int DOCUMENT_FACE = 2;

    static SharedPreferences sharedPreferences;

    private AlertDialog loadingDialog;

    private FragmentManager fragmentManager;
    private FragmentMain mainFragment;
    private FragmentResults resultsFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sharedPreferences = getSharedPreferences(MY_SHARED_PREFS, MODE_PRIVATE);

        fragmentManager = getSupportFragmentManager();
        mainFragment = FragmentMain.getInstance(completion);
        fragmentManager.beginTransaction().replace(R.id.mainFragment, mainFragment).commit();
    }

    @Override
    protected void onResume() {
        super.onResume();

//        if(!DocumentReader.Instance().getDocumentReaderIsReady()) {
            final AlertDialog initDialog = showDialog("Initializing");

            mainFragment.prepareDatabase(MainActivity.this, new IDocumentReaderPrepareCompletion() {
                @Override
                public void onPrepareProgressChanged(int i) {
                    initDialog.setTitle("Downloading database: " + i + "%");
                }

                @Override
                public void onPrepareCompleted(boolean b, Throwable s) {
                    mainFragment.init(MainActivity.this, new IDocumentReaderInitCompletion() {
                        @Override
                        public void onInitCompleted(boolean b, Throwable s) {
                            if (initDialog.isShowing()) {
                                initDialog.dismiss();
                            }

                            if(!b){
                                Toast.makeText(MainActivity.this, "Init failed:" + s, Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                }
            });
//        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if(loadingDialog!=null){
            loadingDialog.dismiss();
            loadingDialog = null;
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();

        if(fragmentManager.getBackStackEntryCount()>0){
            fragmentManager.popBackStack();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            //Image browsing intent processed successfully
            if (requestCode == REQUEST_BROWSE_PICTURE){
                if (data.getData() != null) {
                    Uri selectedImage = data.getData();
                    Bitmap bmp = getBitmap(selectedImage, 1920, 1080);

                    loadingDialog = showDialog("Processing image");

                    DocumentReader.Instance().recognizeImage(bmp, completion);
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //access to gallery is allowed
                    mainFragment.createImageBrowsingRequest();
                } else {
                    Toast.makeText(MainActivity.this, "Permission required, to browse images",Toast.LENGTH_LONG).show();
                }
            } break;
        }
    }

    //DocumentReader processing callback
    private IDocumentReaderCompletion completion = new IDocumentReaderCompletion() {
        @Override
        public void onCompleted(int action, final DocumentReaderResults results, Throwable error) {
            //processing is finished, all results are ready
            if (action == DocReaderAction.COMPLETE) {
                if(loadingDialog!=null && loadingDialog.isShowing()){
                    loadingDialog.dismiss();
                }

                //Checking, if nfc chip reading should be performed
                if (sharedPreferences.getBoolean(DO_RFID, false) && results!=null && results.chipPage != 0) {
                    //setting the chip's access key - mrz on car access number
                    String accessKey = null;
                    if ((accessKey = results.getTextFieldValueByType(eVisualFieldType.FT_MRZ_STRINGS)) != null && !accessKey.isEmpty()) {
                        accessKey = results.getTextFieldValueByType(eVisualFieldType.FT_MRZ_STRINGS)
                                .replace("^", "").replace("\n","");
                        DocumentReader.Instance().rfidScenario().setMrz( accessKey);
                        DocumentReader.Instance().rfidScenario().setPacePasswordType(eRFID_Password_Type.PPT_MRZ);
                    } else if ((accessKey = results.getTextFieldValueByType(eVisualFieldType.FT_CARD_ACCESS_NUMBER)) != null && !accessKey.isEmpty()) {
                        DocumentReader.Instance().rfidScenario().setPassword(accessKey);
                        DocumentReader.Instance().rfidScenario().setPacePasswordType(eRFID_Password_Type.PPT_CAN);
                    }

                    //starting chip reading
                    DocumentReader.Instance().startRFIDReader(MainActivity.this, new IDocumentReaderCompletion() {
                        @Override
                        public void onCompleted(int rfidAction, final DocumentReaderResults results, Throwable error) {
                            if (rfidAction == DocReaderAction.COMPLETE) {
                                final Bitmap rfidPortrait = results.getGraphicFieldImageByType(eGraphicFieldType.GF_PORTRAIT,
                                        eRPRM_ResultType.RFID_RESULT_TYPE_RFID_IMAGE_DATA);
                                if( rfidPortrait!=null ) {
                                    matchFace(results, rfidPortrait);
                                } else {
                                    resultsFragment = FragmentResults.getInstance(results);
                                    fragmentManager.beginTransaction().replace(R.id.mainFragment, resultsFragment).addToBackStack("xxx").commitAllowingStateLoss();
                                }
                            }
                        }
                    });
                } else if( results !=null) {
                    final Bitmap portrait = results.getGraphicFieldImageByType(eGraphicFieldType.GF_PORTRAIT);
                    if(portrait!=null) {
                        matchFace(results, portrait);
                    } else {
                        resultsFragment = FragmentResults.getInstance(results);
                        fragmentManager.beginTransaction().replace(R.id.mainFragment, resultsFragment).addToBackStack("xxx").commitAllowingStateLoss();
                    }
                } else {
                    resultsFragment = FragmentResults.getInstance(results);
                    fragmentManager.beginTransaction().replace(R.id.mainFragment, resultsFragment).addToBackStack("xxx").commitAllowingStateLoss();
                }
            } else {
                //something happened before all results were ready
                if(action==DocReaderAction.CANCEL){
                    Toast.makeText(MainActivity.this, "Scanning was cancelled",Toast.LENGTH_LONG).show();
                } else if(action == DocReaderAction.ERROR){
                    Toast.makeText(MainActivity.this, "Error:" + error, Toast.LENGTH_LONG).show();
                }
            }
        }
    };

    private void matchFace(final DocumentReaderResults results, final Bitmap portrait) {
        FaceReaderService.Instance().setServiceUrl("https://faceapi.regulaforensics.com");

        FaceReaderService.Instance().getFaceFromCamera(MainActivity.this, new FaceCaptureCallback() {
            @Override
            public void onFaceCaptured(int action, final Image capturedFace, String s) {
                if (capturedFace != null) {
                    final MatchFacesRequest request = new MatchFacesRequest();
                    request.similarityThreshold = 0;

                    Image img = new Image();
                    img.id = CAPTURED_FACE;
                    img.tag = ".jpg";
                    img.imageType = eInputFaceType.ift_Live;
                    img.setImage(capturedFace.image());
                    request.images.add(img);

                    Image port = new Image();
                    port.id = DOCUMENT_FACE;
                    port.tag = ".jpg";
                    port.imageType = eInputFaceType.ift_DocumentPrinted;
                    port.setImage(portrait);
                    request.images.add(port);

                    FaceReaderService.Instance().matchFaces(request, new MatchFaceCallback() {
                        @Override
                        public void onFaceMatched(int i, MatchFacesResponse matchFacesResponse, String s) {
                            resultsFragment = FragmentResults.getInstance(results, request, matchFacesResponse);
                            fragmentManager.beginTransaction()
                                    .replace(R.id.mainFragment, resultsFragment).addToBackStack("xxx")
                                    .commitAllowingStateLoss();
                        }
                    });
                }
            }
        });
    }

    private AlertDialog showDialog(String msg) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(MainActivity.this);
        View dialogView = getLayoutInflater().inflate(R.layout.simple_dialog, null);
        dialog.setTitle(msg);
        dialog.setView(dialogView);
        dialog.setCancelable(false);
        return dialog.show();
    }


    // loads bitmap from uri
    private Bitmap getBitmap(Uri selectedImage, int targetWidth, int targetHeight) {
        ContentResolver resolver = MainActivity.this.getContentResolver();
        InputStream is = null;
        try {
            is = resolver.openInputStream(selectedImage);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(is, null, options);

        //Re-reading the input stream to move it's pointer to start
        try {
            is = resolver.openInputStream(selectedImage);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, targetWidth, targetHeight);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return decodeStream(is, null, options);
    }

    // see https://developer.android.com/topic/performance/graphics/load-bitmap.html
    private int calculateInSampleSize(BitmapFactory.Options options, int bitmapWidth, int bitmapHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > bitmapHeight || width > bitmapWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) > bitmapHeight
                    && (halfWidth / inSampleSize) > bitmapWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

}
