package opnamefragment.larsdepauw.be.autoopnamefragment;

        import android.content.Context;
        import android.content.Intent;
        import android.content.SharedPreferences;
        import android.graphics.Bitmap;
        import android.graphics.BitmapFactory;
        import android.graphics.ImageFormat;
        import android.graphics.PixelFormat;
        import android.hardware.Camera;
        import android.os.Bundle;
        import android.util.Log;
        import android.view.SurfaceHolder;
        import android.view.SurfaceView;
        import android.view.View;
        import android.widget.ImageView;
        import android.widget.LinearLayout;
        import android.widget.ProgressBar;
        import android.widget.TextView;
        import android.widget.Toast;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import butterknife.InjectView;
import nl.autodata.opname.AutoDataApp;
import nl.autodata.opname.R;
import nl.autodata.opname.managers.DatabaseManager;
import nl.autodata.opname.models.realm.VehicleSession;
import nl.autodata.opname.models.realm.VoertuigAfbeelding;
import nl.autodata.opname.openalpr.OpenAlprConstants;


public class MultiplePictureIntakeCameraActivity extends BaseActivity implements SurfaceHolder.Callback {
    private Camera camera;
    private final static int SELECT_PICTURE = 2;
    private static final int AFBEELDINGTYPE_ALGEMEEN = 1;

    private String mCurrentCameraPath = "";
    private int mCurrentCameraType = 0;

    private boolean previewing = false;
    private static int pictureFromData;
    private SurfaceHolder surfaceHolder;
    private String afbeeldingUuid;
    private String picturePositionString;
    private static String sessionUuid;
    private static int AFBEELDINGTYPE = 0;
    private static int extraPicturesCount;
    private boolean skipIsPressed;
    private boolean startFromFirstPlaceholder;

    @InjectView(R.id.camerapreview) SurfaceView surfaceView;
    @InjectView(R.id.progressBar) ProgressBar progressBar;
    @InjectView(R.id.car_overlay) ImageView car_overlay;
    @InjectView(R.id.takePicture) ImageView takePicture;
    @InjectView(R.id.btn_skip) TextView btn_skip;
    @InjectView(R.id.ll_multiple_picture_with_overlay) LinearLayout ll_multiple_picture_with_overlay;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multiple_picture_camera_intake_layout);
        getWindow().setFormat(PixelFormat.UNKNOWN);

        skipIsPressed = false;
        final Intent intent = getIntent();
        AFBEELDINGTYPE = intent.getIntExtra("afbeeldingtype", 0);

        startFromFirstPlaceholder = intent.getBooleanExtra("startFromFirstPlaceholder", true);
        pictureFromData = 0;
        final SharedPreferences sharedpreferences = getSharedPreferences("extraPicturesCount", Context.MODE_PRIVATE);
        final SharedPreferences.Editor editor = sharedpreferences.edit();
        sessionUuid = intent.getStringExtra("sessionUuid");
        VehicleSession session = DatabaseManager.getVehicleSession(getRealm(), sessionUuid);
        afbeeldingUuid = intent.getStringExtra("afbeeldingUuid");
        picturePositionString = intent.getStringExtra("position");

        if (AFBEELDINGTYPE != AFBEELDINGTYPE_ALGEMEEN) {
            btn_skip.setVisibility(View.INVISIBLE);
        }

        progressBar.setVisibility(View.INVISIBLE);
        surfaceHolder = surfaceView.getHolder();
        btn_skip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectNextOverlay(pictureFromData);
                camera.startPreview();
                skipIsPressed = true;
            }
        });

        final Camera.PictureCallback mPicture = new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(final byte[] data, final Camera camera) {

                File f = null;
                Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length);

                try {
                    String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                    String imageFileName = OpenAlprConstants.JPEG_FILE_PREFIX + timeStamp + "_";

                    File storageDir = getExternalFilesDir(null);
                    f = File.createTempFile(imageFileName, OpenAlprConstants.JPEG_FILE_SUFFIX, storageDir);
                    mCurrentCameraPath = f.getAbsolutePath();
                    FileOutputStream outStream = null;
                    try {
                        outStream = new FileOutputStream(mCurrentCameraPath);
                        outStream.write(data);
                        outStream.close();
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    extraPicturesCount = sharedpreferences.getInt("extraPicturesCount", 8);
                    VoertuigAfbeelding newPicture = new VoertuigAfbeelding();
                    if (intent.getIntExtra("afbeeldingtype", 0) == AFBEELDINGTYPE_ALGEMEEN) {
                        if (!skipIsPressed && afbeeldingUuid != null) {
                            VoertuigAfbeelding picture = DatabaseManager.getAfbeeldingWithUuid(getRealm(), afbeeldingUuid);

                            newPicture.setPlaceHolder(false);
                            newPicture.setPath(mCurrentCameraPath);
                            newPicture.setOrderInData(pictureFromData);
                            newPicture.setAfbeeldingType(AFBEELDINGTYPE);
                            newPicture.setSessionUuid(sessionUuid);
                            newPicture.setUuid(afbeeldingUuid);

                            if (pictureFromData > 7) {
                                newPicture.setFileName("extra");
                                newPicture.setPicturePosition("extra");
                                newPicture.setOrderInData(extraPicturesCount);
                                extraPicturesCount++;
                                editor.putInt("extraPicturesCount", extraPicturesCount);
                                editor.commit();
                            } else {
                                newPicture.setFileName(returnCarAngleName());
                                newPicture.setPicturePosition(returnCarAngleName());
                            }
                            DatabaseManager.deletePlaceHolderWithOrderInData(AutoDataApp.getContext(), pictureFromData, AFBEELDINGTYPE);
                            DatabaseManager.insertAfbeelding(getBaseContext(), newPicture);
                        } else {

                            newPicture.setPlaceHolder(false);
                            newPicture.setPath(mCurrentCameraPath);
                            newPicture.setOrderInData(pictureFromData);
                            newPicture.setAfbeeldingType(AFBEELDINGTYPE);
                            newPicture.setSessionUuid(sessionUuid);

                            if (pictureFromData > 7) {
                                newPicture.setFileName("extra");
                                newPicture.setPicturePosition("extra");
                                newPicture.setOrderInData(extraPicturesCount);
                                extraPicturesCount++;
                                editor.putInt("extraPicturesCount", extraPicturesCount);
                                editor.commit();
                            } else {
                                newPicture.setFileName(returnCarAngleName());
                                newPicture.setPicturePosition(returnCarAngleName());
                            }
                            DatabaseManager.deletePlaceHolderWithOrderInData(AutoDataApp.getContext(), pictureFromData, AFBEELDINGTYPE);
                            DatabaseManager.insertAfbeelding(getBaseContext(), newPicture);
                        }
                    } else {
                        VoertuigAfbeelding newDamagePicture = new VoertuigAfbeelding();
                        newDamagePicture.setAfbeeldingType(AFBEELDINGTYPE);
                        newDamagePicture.setPath(mCurrentCameraPath);
                        newDamagePicture.setUuid(UUID.randomUUID().toString());
                        newDamagePicture.setSessionUuid(sessionUuid);
                        DatabaseManager.insertAfbeelding(getBaseContext(), newDamagePicture);
                    }

                } catch (Exception e) {
                    Toast.makeText(AutoDataApp.getContext(), R.string.intake_vehicle_picture_camera_fail, Toast.LENGTH_LONG).show();
                    e.printStackTrace();
                    f = null;
                    mCurrentCameraPath = "";
                    mCurrentCameraType = 0;
                }

                if (AFBEELDINGTYPE == AFBEELDINGTYPE_ALGEMEEN) {
                    selectNextOverlay(pictureFromData);
                }
                camera.startPreview();
                progressBar.setVisibility(View.INVISIBLE);
                takePicture.setEnabled(true);
                skipIsPressed = false;
            }
        };

        if (AFBEELDINGTYPE == AFBEELDINGTYPE_ALGEMEEN) {
            selectPictureOverlay(afbeeldingUuid);
            VoertuigAfbeelding thisPicture = DatabaseManager.getAfbeeldingenForUuid(getRealm(), sessionUuid, AFBEELDINGTYPE).get(pictureFromData);
            while (!thisPicture.isPlaceHolder() && pictureFromData < 7) {
                selectNextOverlay(pictureFromData);
                thisPicture = DatabaseManager.getAfbeeldingenForUuid(getRealm(), sessionUuid, AFBEELDINGTYPE).get(pictureFromData);
            }
        }
        ll_multiple_picture_with_overlay.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        camera.autoFocus(new Camera.AutoFocusCallback() {
                            @Override
                            public void onAutoFocus(boolean success, Camera camera) {
                            }
                        });
                    }
                }
        );

        View.OnClickListener pictureOnClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePicture.setEnabled(false);
                camera.takePicture(null, null, mPicture);
                progressBar.setVisibility(View.VISIBLE);
            }
        };

        takePicture.setOnClickListener(pictureOnClickListener);
        surfaceHolder.addCallback(this);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

        if (previewing) {
            camera.stopPreview();
            previewing = false;
        }
        if (camera != null) {
            try {
                camera.setPreviewDisplay(surfaceHolder);
                camera.setDisplayOrientation(90);
                camera.startPreview();
                previewing = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        camera = Camera.open();
        Camera.Parameters p = camera.getParameters();
        List<Camera.Size> sizes = p.getSupportedPictureSizes();
        Camera.Size size = sizes.get(0);
        for (int i = 0; i < sizes.size(); i++) {
            if (sizes.get(i).width > size.width)
                size = sizes.get(i);
        }
        p.setPictureSize(size.width, size.height);
        Log.i("picture width:", size.width + "");
        Log.i("picture height:", size.height + "");
        p.setRotation(90);
        p.setJpegQuality(100);
        p.setJpegThumbnailQuality(100);
        p.setPictureFormat(ImageFormat.JPEG);

        camera.setParameters(p);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        camera.stopPreview();
        camera.release();
        camera = null;
        previewing = false;
    }

    public String returnCarAngleName() {
        String name = "";
        if (AFBEELDINGTYPE == AFBEELDINGTYPE_ALGEMEEN) {
            switch (pictureFromData) {
                case (0):
                    name = "ELF";
                    break;
                case (1):
                    name = "EL";
                    break;
                case (2):
                    name = "ELB";
                    break;
                case (3):
                    name = "EB";
                    break;
                case (4):
                    name = "ERB";
                    break;
                case (5):
                    name = "ER";
                    break;
                case (6):
                    name = "ERF";
                    break;
                case (7):
                    name = "EF";
                    break;
                default:
            }
        }
        return name;
    }

    String picturePosition = "";

    public void selectPictureOverlay(String afbeeldingUuid) {
        if (afbeeldingUuid != null) {
            VoertuigAfbeelding voertuigAfbeelding = DatabaseManager.getAfbeeldingWithUuid(getRealm(), afbeeldingUuid);
            if (voertuigAfbeelding != null) {
                picturePosition = voertuigAfbeelding.getPicturePosition();
            }
        }

        switch (picturePosition) {
            case ("ELF"):
                pictureFromData = 0;
                break;
            case ("EL"):
                pictureFromData = 1;
                break;
            case ("ELB"):
                pictureFromData = 2;
                break;
            case ("EB"):
                pictureFromData = 3;
                break;
            case ("ERB"):
                pictureFromData = 4;
                break;
            case ("ER"):
                pictureFromData = 5;
                break;
            case ("ERF"):
                pictureFromData = 6;
                break;
            case ("EF"):
                pictureFromData = 7;
                break;
        }

        switch (pictureFromData) {
            case (0):
                car_overlay.setImageResource(R.drawable.elf);
                break;
            case (1):
                car_overlay.setImageResource(R.drawable.el);
                break;
            case (2):
                car_overlay.setImageResource(R.drawable.elb);
                break;
            case (3):
                car_overlay.setImageResource(R.drawable.eb);
                break;
            case (4):
                car_overlay.setImageResource(R.drawable.erb);
                break;
            case (5):
                car_overlay.setImageResource(R.drawable.er);
                break;
            case (6):
                car_overlay.setImageResource(R.drawable.erf);
                break;
            case (7):
                car_overlay.setImageResource(R.drawable.ef);
                break;
            case (8):
                car_overlay.setImageDrawable(null);
            default:
                car_overlay.setImageDrawable(null);
                break;
        }
        if (pictureFromData >= 8) {
            btn_skip.setVisibility(View.INVISIBLE);
        }
    }

    public void selectNextOverlay(int position) {
        pictureFromData++;
        if (pictureFromData < 8) {
            VoertuigAfbeelding thisPicture = DatabaseManager.getAfbeeldingenForUuid(getRealm(), sessionUuid, AFBEELDINGTYPE).get(pictureFromData);
            while (!thisPicture.isPlaceHolder() && pictureFromData < 8) {
                selectNextOverlay(pictureFromData);
                if (pictureFromData < 8) {
                    thisPicture = DatabaseManager.getAfbeeldingenForUuid(getRealm(), sessionUuid, AFBEELDINGTYPE).get(pictureFromData);
                }
            }
        }
        switch (pictureFromData) {
            case (0):
                car_overlay.setImageResource(R.drawable.elf);
                break;
            case (1):
                car_overlay.setImageResource(R.drawable.el);
                break;
            case (2):
                car_overlay.setImageResource(R.drawable.elb);
                break;
            case (3):
                car_overlay.setImageResource(R.drawable.eb);
                break;
            case (4):
                car_overlay.setImageResource(R.drawable.erb);
                break;
            case (5):
                car_overlay.setImageResource(R.drawable.er);
                break;
            case (6):
                car_overlay.setImageResource(R.drawable.erf);
                break;
            case (7):
                car_overlay.setImageResource(R.drawable.ef);
                break;
            case (8):
                car_overlay.setImageDrawable(null);

            default:
                car_overlay.setImageDrawable(null);
                break;
        }
        if (pictureFromData >= 8) {
            btn_skip.setVisibility(View.INVISIBLE);
        }
    }
}