package com.richard.weger.wqc.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.provider.MediaStore;
import android.os.Bundle;
import android.support.v4.content.FileProvider;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.richard.weger.wqc.R;
import com.richard.weger.wqc.domain.Item;
import com.richard.weger.wqc.domain.Project;
import com.richard.weger.wqc.domain.Report;
import com.richard.weger.wqc.helper.FileHelper;
import com.richard.weger.wqc.helper.ImageHelper;
import com.richard.weger.wqc.helper.ProjectHelper;
import com.richard.weger.wqc.helper.StringHelper;
import com.richard.weger.wqc.util.DeviceManager;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;

import uk.co.senab.photoview.PhotoViewAttacher;

import static com.richard.weger.wqc.constants.AppConstants.*;

public class PictureViewerActivity extends Activity{

    Item item = new Item();
    int position = -1;
    PhotoViewAttacher mAttacher;
    ImageView imageView;
    String futurePath = "";
    Project project;
    Report report;
    String mode;
    ArrayList<String> takenPictures = new ArrayList<>();

    @Override
    public void onBackPressed(){}

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_picture_viewer);

        imageView = findViewById(R.id.ivItem);

        Intent intent = getIntent();
        if(intent != null){

            project = (Project) intent.getSerializableExtra(PROJECT_KEY);
            ProjectHelper.linkReferences(project);

            mode = intent.getStringExtra(PICTURE_CAPTURE_MODE);
            if(mode.equals(ITEM_PICTURE_MODE)) {
                report = (Report) intent.getSerializableExtra(REPORT_KEY);
                item = (Item) intent.getSerializableExtra(ITEM_KEY);
                position = intent.getIntExtra(ITEM_ID_KEY, -1);
                if(position == -1){
                    Toast.makeText(this, R.string.dataRecoverError, Toast.LENGTH_LONG).show();
                    resultCanceled();
                }

                if(item != null){
                    if (item.getPicture() != null && item.getPicture().getFileName() != null){
                        File file = new File(StringHelper.getPicturesFolderPath(project).concat(item.getPicture().getFileName()));
                        if(file.exists() && FileHelper.isValidFile(file.getPath())){
                            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
                            if(bitmap != null) {
                                imageView.setImageBitmap(bitmap);
                            }
                        }
                    }
                }
            } else {
                if(intent.hasExtra(PICTURE_FILEPATH_KEY)){
                    String filePath = intent.getStringExtra(PICTURE_FILEPATH_KEY);
                    if(filePath != null) {
                        findViewById(R.id.btnTakeNew).setVisibility(View.INVISIBLE);
                        File file = new File(filePath);
                        if (file.exists() && FileHelper.isValidFile(file.getPath())) {
                            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
                            if (bitmap != null) {
                                imageView.setImageBitmap(bitmap);
                            }
                        }
                    } else {
                        takePicture();
                    }
                } else {
                    takePicture();
                }
            }
        }

        if(DeviceManager.getCurrentDevice().getRole().toLowerCase().equals("te") || project.getDrawingRefs().get(0).isFinished()){
            findViewById(R.id.btnTakeNew).setVisibility(View.INVISIBLE);
        }

        findViewById(R.id.btnTakeNew).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePicture();
            }
        });
        findViewById(R.id.btnCancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resultCanceled();
            }
        });

        mAttacher = new PhotoViewAttacher(imageView);
    }

    private void resultCanceled(){
        setResult(RESULT_CANCELED);
        finish();
    }
    private void takePicture() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile;
            try {
                photoFile = createImageFile();
            } catch (Exception ex) {
                Toast.makeText(this, "Error when trying to create the image file",
                        Toast.LENGTH_LONG).show();
                return;
            }
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        PICTURES_AUTHORITY,
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE_ACTION);
            }
        }
    }

    private File createImageFile(){
        // Create an image file name
        // String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName;
        if(mode.equals(ITEM_PICTURE_MODE)) {
            imageFileName = item.getPicture().getFileName();
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM-dd-yyyy_hh:mm:ss");
            imageFileName = project.getReference()
                    .concat("Z").concat(String.valueOf(project.getDrawingRefs().get(0).getNumber()))
                    .concat("T").concat(String.valueOf(project.getDrawingRefs().get(0).getParts().get(0).getNumber()))
                    .concat("QP").concat(String.valueOf(ProjectHelper.getCurrentPicNumber(project)))
//                    .concat("_")
//                    .concat(sdf.format(Calendar.getInstance().getTime()))
                    .concat(".jpg");
        }
        String folderPath = StringHelper.getProjectFolderPath(project).concat("Pictures/");
        File storageDir = new File(folderPath);
//        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if(!storageDir.exists()){
            storageDir.mkdirs();
        }

        if(!imageFileName.contains(folderPath)){
            imageFileName = folderPath.concat(imageFileName);
        }

        File image;
        try {
            image = new File(imageFileName.replace(".jpg","_new.jpg"));
            if(image.exists()){
                image.delete();
            }
//            FileWriter fileWriter = new FileWriter(image.getAbsolutePath(), false);
            image.createNewFile();
//            image = File.createTempFile(imageFileName,".jpg", storageDir);
        } catch (Exception e) {
            try{
                imageFileName = imageFileName.substring(
                        imageFileName.lastIndexOf('/') + 1,
                        imageFileName.lastIndexOf('.'));
                        if(imageFileName.length() - imageFileName.lastIndexOf('-') > 15)
                            imageFileName = imageFileName.substring(0,
                                    imageFileName.lastIndexOf('-') + 9);
                image = File.createTempFile(
                        imageFileName,  /* prefix */
                        ".jpg",         /* suffix */
                        storageDir      /* directory */
                );
            }
            catch (IOException e2) {
                e2.printStackTrace();
                return null;
            }
        }
        // Save a file: path for use with ACTION_VIEW intents
        futurePath = image.getAbsolutePath();
        return image;
    }

//    private void setPic() {
//        // Get the dimensions of the View
//        ImageView mImageView = findViewById(R.id.ivItem);
//        int targetW = mImageView.getWidth();
//        int targetH = mImageView.getHeight();
//
//        // Get the dimensions of the bitmap
//        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
//        bmOptions.inJustDecodeBounds = true;
//        BitmapFactory.decodeFile(item.getPicture().getFileName(), bmOptions);
//        int photoW = bmOptions.outWidth;
//        int photoH = bmOptions.outHeight;
//
//        // Determine how much to scale down the image
//        int scaleFactor = Math.min(photoW/targetW, photoH/targetH);
//
//        // Decode the image file into a Bitmap sized to fill the View
//        bmOptions.inJustDecodeBounds = false;
//        bmOptions.inSampleSize = scaleFactor;
//        bmOptions.inPurgeable = true;
//
//        Bitmap bitmap = BitmapFactory.decodeFile(item.getPicture().getFileName(), bmOptions);
//        mImageView.setImageBitmap(bitmap);
//    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Bitmap bitmap = CameraHelper.handleTakePictureIntentResponse(requestCode, resultCode, data);
        // item.getPicture().setProxyBitmap(new ProxyBitmap(bitmap));
        if(resultCode == RESULT_OK) {
            putTimeStamp();

            try {
                String finalPath, compressedPath;

                compressedPath = (new ImageHelper()).compressImage(futurePath);
                FileHelper.fileDelete(futurePath);

                finalPath = futurePath.replace("_new","");
                FileHelper.fileCopy(new File(compressedPath), new File(finalPath));
                FileHelper.fileDelete(compressedPath);

                futurePath = finalPath;
                takenPictures.add(futurePath);

            } catch (Exception ex){
                ex.printStackTrace();
            }

//            item.getPicture().setFileName(futurePath);

            if(mode.equals(GENERAL_PICTURE_MODE)) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(R.string.getMorePicturesTag);
                builder.setCancelable(false);
                builder.setNegativeButton(R.string.noTag, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finishTakingPictures();
                    }
                });
                builder.setPositiveButton(R.string.yesTAG, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        takePicture();
                    }
                });
                builder.show();
            } else {
                finishTakingPictures();
            }
        } else {
            File file = new File(futurePath);
            if(file.exists()){
                file.delete()
            }
        }
    }

    private void finishTakingPictures(){
        Intent intent = new Intent();
        if(mode.equals(GENERAL_PICTURE_MODE)){
            intent.putStringArrayListExtra(TAKEN_PICTURES_KEY, takenPictures);
        }
        intent.putExtra(PICTURE_CAPTURE_MODE, mode);
        intent.putExtra(ITEM_ID_KEY, position);
        intent.putExtra(ITEM_KEY, item);
        setResult(RESULT_OK, intent);
        finish();
    }

    private void putTimeStamp(){
        Bitmap src = BitmapFactory.decodeFile(futurePath);
        Bitmap dest = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        String dateTime = SDF.format(Calendar.getInstance().getTime());
        String projectLabel, drawingLabel, partLabel;
        projectLabel = getResources().getString(R.string.projectLabel);
        drawingLabel = getResources().getString(R.string.drawingLabel);
        partLabel = getResources().getString(R.string.partLabel);
        String projectInfo = projectLabel
                .concat(": ").concat(project.getReference())
                .concat("\n").concat(drawingLabel).concat(": ").concat(String.valueOf(project.getDrawingRefs().get(0).getNumber()))
                .concat("\n").concat(partLabel).concat(": ").concat(String.valueOf(project.getDrawingRefs().get(0).getParts().get(0).getNumber()))
                .concat("\n");
        String itemInfo;

        Canvas cs = new Canvas(dest);
        Paint tPaint = new Paint();
        tPaint.setTextSize(85);
        tPaint.setColor(Color.YELLOW);
        tPaint.setStyle(Paint.Style.FILL);
        float height = tPaint.measureText("yY");
        cs.drawBitmap(src, 0f, 0f, null);
        cs.drawText(projectInfo.concat(" - ").concat(dateTime),20f, height + 15f, tPaint);
        if(mode.equals(ITEM_PICTURE_MODE)) {
            itemInfo = report.toString().concat(", ").concat(getResources().getString(R.string.itemTag))
                    .concat(": ").concat(String.valueOf(item.getNumber()));

        } else {

            itemInfo = "QP" + ProjectHelper.getCurrentPicNumber(project);
        }
        cs.drawText(itemInfo,20f, 2*height + 15f, tPaint);

        try {
            dest.compress(Bitmap.CompressFormat.JPEG, 100, new FileOutputStream(new File(futurePath)));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }
}
