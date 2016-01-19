package opnamefragment.larsdepauw.be.autoopnamefragment;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.List;

import nl.autodata.opname.AutoDataApp;
import nl.autodata.opname.R;
import nl.autodata.opname.activities.MainActivity;
import nl.autodata.opname.activities.MultiplePictureIntakeCameraActivity;
import nl.autodata.opname.adapters.VoertuigAfbeeldingAdapter;
import nl.autodata.opname.managers.DatabaseManager;
import nl.autodata.opname.models.realm.VehicleSession;
import nl.autodata.opname.models.realm.VoertuigAfbeelding;
import nl.autodata.opname.models.voertuiginfo.Voertuiginfo;
import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;
import io.realm.RealmResults;


public class VehicleIntakePicturesTabFragment extends BaseFragment {
    private static final String TAG = VehicleIntakePicturesTabFragment.class.getSimpleName();
    private static final int AFBEELDINGTYPE_ALGEMEEN = 1;
    private static final int PICTURE_INTENT_PLACEHOLDER = 2;
    private static final int PICTURE_INTENT_BUTTON = 3;
    private static final int GALLERY_INTENT_SINGLE_PICTURE = 4;
    private static final int GALLERY_INTENT_MULTIPLE_PICTURE = 5;

    private static int orderOfClickedPlaceholder = 0;
    private VoertuigAfbeeldingAdapter adapter;
    private Intent goToCameraWithPosition;
    private VehicleSession vehicleSession;
    private static int tabId = 0;
    private Intent pictureIntent;
    private String uuid;
    private List<String> chosenPaths;

    @InjectView(R.id.rv_pictures)
    RecyclerView rvPictures;

    @InjectView(R.id.txt_no_pictures)
    TextView txtNoPictures;

    RealmResults<VoertuigAfbeelding> data;

    @OnClick(R.id.btn_add_picture)
    public void addPicture(View view) {
        selectImage(PICTURE_INTENT_BUTTON);
    }

    public static VehicleIntakePicturesTabFragment newInstance(Bundle bundle) {
        VehicleIntakePicturesTabFragment f = new VehicleIntakePicturesTabFragment();

        if (bundle != null) {
            f.setArguments(bundle);
        }
        return f;
    }

    @Override
    public void onViewStateRestored(Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        String path = ((MainActivity) getActivity()).getPathOfCHosenPicture();
        ((MainActivity)getActivity()).deletePicturePathValue();
        if (!TextUtils.isEmpty(path)) {
            if (!chosenPaths.contains(path)) {
                addSingleGalleryPhoto(path);
            }
        }
        setupPictures();
        updatePictures();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_vehicle_intake_pictures_tab, container, false);
        ButterKnife.inject(this, root);
        Bundle args = getArguments();
        tabId = args.getInt("title");
        chosenPaths = new ArrayList<>();
        Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();

        uuid = args.getString("uuid");
        txtNoPictures.setVisibility(View.INVISIBLE);
        vehicleSession = DatabaseManager.getVehicleSession(getRealm(), uuid);
        goToCameraWithPosition = new Intent(AutoDataApp.getContext(), MultiplePictureIntakeCameraActivity.class);

        pictureIntent = new Intent(getActivity().getBaseContext(), MultiplePictureIntakeCameraActivity.class);
        pictureIntent.putExtra("afbeeldingtype", AFBEELDINGTYPE_ALGEMEEN);
        pictureIntent.putExtra("sessionUuid", vehicleSession.getUuid());
        pictureIntent.putExtra("startFromFirstPlaceholder", true);
        setupPictures();
        return root;
    }

    private void selectImage(final int intentType) {
        new MaterialDialog.Builder(getActivity())
                .title(R.string.intake_vehicle_picture_choose_dialoge_title)
                .iconRes(R.drawable.ic_autodata)
                .limitIconToDefaultSize()
                .content(R.string.intake_vehicle_picture_load_picture_with)
                .positiveText(R.string.intake_vehicle_picture_choose_camera)
                .negativeText(R.string.intake_vehicle_picture_choose_gallery)
                .callback(new MaterialDialog.ButtonCallback() {

                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        super.onPositive(dialog);
                        switch (intentType) {
                            case (PICTURE_INTENT_BUTTON):
                                startActivity(pictureIntent);
                                break;
                            case (PICTURE_INTENT_PLACEHOLDER):
                                startActivity(goToCameraWithPosition);
                                break;
                        }
                        dialog.dismiss();
                    }

                    @Override
                    public void onNegative(MaterialDialog dialog) {
                        super.onNegative(dialog);
                        Intent intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                        getActivity().setResult(Activity.RESULT_OK, intent);
                        intent.setType("image/*");

                        switch (intentType) {
                            case (PICTURE_INTENT_BUTTON):
                                getActivity().startActivityForResult(intent, GALLERY_INTENT_MULTIPLE_PICTURE);
                                break;
                            case (PICTURE_INTENT_PLACEHOLDER):
                                getActivity().startActivityForResult(intent, GALLERY_INTENT_SINGLE_PICTURE);
                                break;
                        }
                    }
                })
                .cancelable(true)
                .show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (data == null) {
            Toast.makeText(AutoDataApp.getContext(), R.string.intake_vehicle_picture_no_choice_made, Toast.LENGTH_SHORT).show();
        } else {
            if (resultCode == Activity.RESULT_OK) {

                switch (requestCode) {
                    case (GALLERY_INTENT_SINGLE_PICTURE):
                        Uri selectedImage = data.getData();
                        String[] projection = {MediaStore.Images.Media.DATA};
                        Cursor cursor = AutoDataApp.getContext().getContentResolver().query(selectedImage, projection, null, null, null);
                        cursor.moveToFirst();

                        int columnIndex = cursor.getColumnIndex(projection[0]);
                        String picturePath = cursor.getString(columnIndex);
                        cursor.close();

                        VoertuigAfbeelding newPicture = new VoertuigAfbeelding();
                        newPicture.setAfbeeldingType(AFBEELDINGTYPE_ALGEMEEN);
                        newPicture.setOrderInData(orderOfClickedPlaceholder);
                        newPicture.setPicturePosition(returnCarAngleName());
                        newPicture.setPlaceHolder(false);
                        newPicture.setPath(picturePath);
                        newPicture.setSessionUuid(uuid);

                        DatabaseManager.deletePlaceHolderWithOrderInData(AutoDataApp.getContext(), orderOfClickedPlaceholder, AFBEELDINGTYPE_ALGEMEEN);
                        DatabaseManager.insertAfbeelding(AutoDataApp.getContext(), newPicture);

                        break;

                    case (GALLERY_INTENT_MULTIPLE_PICTURE):
                        break;
                }
            }
        }
        updatePictures();
    }

    private void addSingleGalleryPhoto(String picturePath) {
        VoertuigAfbeelding newPicture = new VoertuigAfbeelding();
        newPicture.setAfbeeldingType(AFBEELDINGTYPE_ALGEMEEN);
        newPicture.setOrderInData(orderOfClickedPlaceholder);
        newPicture.setPicturePosition(returnCarAngleName());
        newPicture.setPlaceHolder(false);
        newPicture.setPath(picturePath);
        newPicture.setSessionUuid(uuid);

        DatabaseManager.deletePlaceHolderWithOrderInData(AutoDataApp.getContext(), orderOfClickedPlaceholder, AFBEELDINGTYPE_ALGEMEEN);
        DatabaseManager.insertAfbeelding(AutoDataApp.getContext(), newPicture);
    }

    private void setupPictures() {
        data = DatabaseManager.getAfbeeldingenForUuid(getRealm(), vehicleSession.getUuid(), AFBEELDINGTYPE_ALGEMEEN);
        final GridLayoutManager layoutManager = new GridLayoutManager(AutoDataApp.getContext(), 2);
        rvPictures.setLayoutManager(layoutManager);

        adapter = new VoertuigAfbeeldingAdapter(data);
        adapter.setOnItemClickListener(new VoertuigAfbeeldingAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(View v, int position) {
                orderOfClickedPlaceholder = position;
                VoertuigAfbeelding voertuigAfbeelding = data.get(position);
                goToCameraWithPosition.putExtra("sessionUuid", vehicleSession.getUuid());
                goToCameraWithPosition.putExtra("afbeeldingUuid", voertuigAfbeelding.getUuid());
                goToCameraWithPosition.putExtra("afbeeldingtype", AFBEELDINGTYPE_ALGEMEEN);
                selectImage(PICTURE_INTENT_PLACEHOLDER);
            }
        });

        adapter.setOnDeleteClickListener(new VoertuigAfbeeldingAdapter.OnDeleteClickListener() {
            @Override
            public void onDelete(View v, final int position) {
                new MaterialDialog.Builder(getActivity())
                        .title(getString(R.string.dialog_intake_picture_delete_title))
                        .iconRes(R.drawable.ic_autodata)
                        .limitIconToDefaultSize()
                        .content(R.string.dialog_intake_picture_delete_text)
                        .positiveText(android.R.string.ok)
                        .negativeText(R.string.cancel)
                        .callback(new MaterialDialog.ButtonCallback() {
                            @Override
                            public void onPositive(MaterialDialog dialog) {
                                super.onPositive(dialog);
                                if (chosenPaths.contains(data.get(position).getPath())) {
                                    chosenPaths.remove(data.get(position).getPath());
                                }
                                DatabaseManager.deleteImageWithUuid(getRealm(), data.get(position).getUuid());
                                ((MainActivity) getActivity()).addSpecificAlgemeenPlaceholder(position);
                                updatePictures();
                                dialog.dismiss();
                            }

                            @Override
                            public void onNegative(MaterialDialog dialog) {
                                super.onNegative(dialog);
                                dialog.dismiss();
                            }
                        })
                        .cancelable(true)
                        .show();
            }
        });

        rvPictures.setAdapter(adapter);
    }

    public void updatePictures() {
        adapter.notifyDataSetChanged();
    }

    public String returnCarAngleName() {
        String name = "";
        switch (orderOfClickedPlaceholder) {
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
        return name;
    }
}
