package com.richard.weger.wqc.activity;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.richard.weger.wqc.R;
import com.richard.weger.wqc.adapter.ItemAdapter;
import com.richard.weger.wqc.domain.DomainEntity;
import com.richard.weger.wqc.domain.Item;
import com.richard.weger.wqc.domain.ItemReport;
import com.richard.weger.wqc.domain.ParamConfigurations;
import com.richard.weger.wqc.domain.Project;
import com.richard.weger.wqc.firebird.FirebirdMessagingService;
import com.richard.weger.wqc.helper.DeviceHelper;
import com.richard.weger.wqc.helper.FileHelper;
import com.richard.weger.wqc.helper.MessageboxHelper;
import com.richard.weger.wqc.helper.ProjectHelper;
import com.richard.weger.wqc.helper.StringHelper;
import com.richard.weger.wqc.rest.entity.EntityRestResult;
import com.richard.weger.wqc.rest.entity.EntityRestTemplateHelper;
import com.richard.weger.wqc.rest.file.FileRestResult;
import com.richard.weger.wqc.rest.file.FileRestTemplateHelper;
import com.richard.weger.wqc.service.ErrorResponseHandler;
import com.richard.weger.wqc.service.ItemRequestParametersResolver;
import com.richard.weger.wqc.service.ProjectRequestParametersResolver;
import com.richard.weger.wqc.service.ReportRequestParametersResolver;

import org.springframework.http.HttpStatus;

import java.util.Map;

import static com.richard.weger.wqc.appconstants.AppConstants.ITEM_ID_KEY;
import static com.richard.weger.wqc.appconstants.AppConstants.ITEM_KEY;
import static com.richard.weger.wqc.appconstants.AppConstants.ITEM_NOT_CHECKED_KEY;
import static com.richard.weger.wqc.appconstants.AppConstants.ITEM_PICTURE_MODE;
import static com.richard.weger.wqc.appconstants.AppConstants.PARAMCONFIG_KEY;
import static com.richard.weger.wqc.appconstants.AppConstants.PICTURE_CAPTURE_MODE;
import static com.richard.weger.wqc.appconstants.AppConstants.PICTURE_VIEWER_SCREEN_ID;
import static com.richard.weger.wqc.appconstants.AppConstants.PROJECT_KEY;
import static com.richard.weger.wqc.appconstants.AppConstants.REPORT_ID_KEY;
import static com.richard.weger.wqc.appconstants.AppConstants.REPORT_KEY;
import static com.richard.weger.wqc.appconstants.AppConstants.REST_ITEMSAVE_KEY;
import static com.richard.weger.wqc.appconstants.AppConstants.REST_PICTUREDOWNLOAD_KEY;
import static com.richard.weger.wqc.appconstants.AppConstants.REST_PICTUREUPLOAD_KEY;
import static com.richard.weger.wqc.appconstants.AppConstants.REST_QRPROJECTLOAD_KEY;
import static com.richard.weger.wqc.appconstants.AppConstants.REST_REPORTUPLOAD_KEY;
import static com.richard.weger.wqc.helper.LogHelper.writeData;

public class ItemReportEditActivity extends ListActivity implements ItemAdapter.ChangeListener,
        EntityRestTemplateHelper.EntityRestResponse,
        FileRestTemplateHelper.FileRestResponse,
        FirebirdMessagingService.FirebaseListener {

    ItemReport report;
    Project project;
    ItemAdapter itemAdapter;
    ParamConfigurations conf;
    int lastItemId = -1;
    boolean canEdit = true;

    boolean schedulePicSave = false;

    Runnable runnable;
    Handler handler = new Handler();
    boolean paused = false;

    private void setRunnable(){
        final int interval = 1000;
        runnable = () -> {
            if(!checkInternetConnection()){
                setWaitingLayout();
                paused = true;
            } else {
                inflateActivityLayout();
                if(paused){
                    projectLoad();
                    paused = false;
                }
            }
        };
        handler.postAtTime(runnable, System.currentTimeMillis() + interval);
        handler.postDelayed(runnable, interval);
    }

    private void setWaitingLayout(){
        setContentView(R.layout.activity_wait);
        (findViewById(R.id.pbWelcome)).setVisibility(View.VISIBLE);
        ((findViewById(R.id.btnExit))).setOnClickListener(v -> {
            finish();
            onDestroy();
        });
    }

    private boolean checkInternetConnection(){
        ConnectivityManager cm =
                (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }

    @Override
    public void onBackPressed() {
        /*if (!shouldAllowBack()) {
            doSomething();
        } else {
            super.onBackPressed();
        }
        */
    }

    @Override
    protected void onResume(){
        super.onResume();
        FirebirdMessagingService.delegate = this;
        setRunnable();
    }

    @Override
    protected void onPause(){
        super.onPause();
        handler.removeCallbacks(runnable);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        writeData("Started intent data get");
        Intent intent = getIntent();
        if(intent != null) {
            Long id;
            id = intent.getLongExtra(REPORT_ID_KEY, -1);
            if(id < 0){
                writeData("Invalid id got from intent. Aborting");
                setResult(RESULT_CANCELED);
                finish();
            }
            conf = (ParamConfigurations) intent.getSerializableExtra(PARAMCONFIG_KEY);
            project = (Project) intent.getSerializableExtra(PROJECT_KEY);
            ProjectHelper.linkReferences(project);
            report = (ItemReport) project.getDrawingRefs().get(0).getReports().stream().filter(r -> r.getId().equals(id)).findFirst().orElse(null);
        }
        if(report == null || project == null){
            writeData("Invalid project and / or report. Closing report edit screen.");
            Toast.makeText(this, R.string.dataRecoverError,Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        inflateActivityLayout();
    }

    private void inflateActivityLayout(){
        setContentView(R.layout.activity_item_report_edit);
        writeData("Setting item adapter");
        itemAdapter = new ItemAdapter(this, report.getItems(), project);
        setListAdapter(itemAdapter);
        itemAdapter.setChangeListener(this);
        setListeners();
        setTextViews();
        toggleControls(true);

        if(DeviceHelper.isOnlyRole("te")){
            itemAdapter.setEnabled(false);
            (findViewById(R.id.chkFinished)).setEnabled(false);
            (findViewById(R.id.chkFinished)).setClickable(false);
        } else {
            itemAdapter.setEnabled(!report.isFinished());
            (findViewById(R.id.chkFinished)).setEnabled(true);
            (findViewById(R.id.chkFinished)).setClickable(true);
        }
        itemAdapter.setCameraEnabled(true);
        itemAdapter.notifyDataSetChanged();

    }

    private void setTextViews(){
        writeData("Setting text views");
        ((TextView)findViewById(R.id.tvProjectInfo)).setText(
                String.format(getResources().getConfiguration().getLocales().get(0),
                        "%s - Z%d",
                        project.getReference(),
                        project.getDrawingRefs().get(0).getDnumber()
//                        ,project.getDrawingRefs().getContext(0).getPart().getContext(0).getNumber()
                        )
        );
        ((TextView)findViewById(R.id.tvReportType)).setText(report.toString());


        updatePendingItemsCount();
    }

    private void setListeners(){
        writeData("Setting listeners");
        Button btn = findViewById(R.id.btnCancel);
        btn.setOnClickListener(v -> close(false));

        CheckBox chkFinished = findViewById(R.id.chkFinished);
        chkFinished.setOnClickListener((view) ->
        {
            toggleControls(false);
            if(report.getItems().stream().noneMatch(i -> i.getStatus() == ITEM_NOT_CHECKED_KEY) || report.isFinished()) {
                shouldChangeReportState();
            } else {
                MessageboxHelper.showMessage(this,
                        getResources().getString(R.string.pendingItemsMessage),
                        getResources().getString(R.string.okTag),
                        this::cancelReportFinish);
            }
        });
    }

    private void shouldChangeReportState(){
        if(!report.isFinished()) {
            MessageboxHelper.showMessage(this,
                    getResources().getString(R.string.confirmationNeeded),
                    getResources().getString(R.string.reportFinishMessage),
                    getResources().getString(R.string.yesTAG),
                    getResources().getString(R.string.noTag),
                    () -> reportFinish(true),
                    this::cancelReportFinish);
        } else {
            MessageboxHelper.showMessage(this,
                    getResources().getString(R.string.confirmationNeeded),
                    getResources().getString(R.string.reportUnfinishMessage),
                    getResources().getString(R.string.yesTAG),
                    getResources().getString(R.string.noTag),
                    () -> reportFinish(false),
                    this::cancelReportFinish);
        }
    }

    private void cancelReportFinish(){
        toggleControls(true);
        ((CheckBox) findViewById(R.id.chkFinished)).setChecked(report.isFinished());
    }

    private void reportFinish(boolean finish){
        toggleControls(false);
        if(report.getClient() == null || report.getClient().isEmpty()){
            MessageboxHelper.getString(this, getResources().getString(R.string.clientNameInformMessage), (content) -> {
                if(content != null && !content.isEmpty()) {
                    report.setClient(content);
                    reportFinish(!report.isFinished());
                } else {
                    MessageboxHelper.showMessage(this,
                            getResources().getString(R.string.emptyFieldsError),
                            getResources().getString(R.string.okTag),
                            () -> toggleControls(true));
                }
            });
        } else {
            report.setFinished(finish);
            ReportRequestParametersResolver resolver = new ReportRequestParametersResolver(REST_REPORTUPLOAD_KEY, conf,false);
            resolver.postEntity(report, this);
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        writeData("Started activity result handling");
        if(resultCode == RESULT_OK){
            if(requestCode == PICTURE_VIEWER_SCREEN_ID){
                Item newItem = (Item) data.getSerializableExtra(ITEM_KEY);
                int itemId = data.getIntExtra(ITEM_ID_KEY, -1);
                if(itemId > -1) {
                    lastItemId = itemId;
                    report.getItems().set(itemId, newItem);
                    itemAdapter.notifyDataSetChanged();
                    save(report.getItems().get(itemId), true);
                }
            }
        }
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        if(canEdit) {
            super.onListItemClick(l, v, position, id);
        }
    }

    public void updatePendingItemsCount(){
        writeData("Updating pending items count");
        long pendingItems = report.getItems().stream().filter(i -> i.getStatus() == ITEM_NOT_CHECKED_KEY).count();
        ((TextView)findViewById(R.id.tvPendingItems)).setText(
                String.format(getResources().getConfiguration().getLocales().get(0), "%s: %d",
                        getResources().getString(R.string.pendingItemsLabel),
                        pendingItems));
    }

    @Override
    public void toggleControls(boolean bResume){
        writeData("Toggling screen controls");
        canEdit = bResume;
        itemAdapter.setEnabled(bResume && !report.isFinished() && !DeviceHelper.isOnlyRole("te"));
        itemAdapter.notifyDataSetChanged();
        if(bResume){
            findViewById(R.id.pbItemReportEdit).setVisibility(View.INVISIBLE);
        }
        else{
            findViewById(R.id.pbItemReportEdit).setVisibility(View.VISIBLE);
        }
        (findViewById(R.id.chkFinished)).setEnabled(bResume && !DeviceHelper.isOnlyRole("te"));
        ((CheckBox) findViewById(R.id.chkFinished)).setChecked(report.isFinished());
        itemAdapter.setCameraEnabled(true);
    }

    @Override
    public void onError() {

    }

    private void save(Item item, boolean savePicture){
        writeData("Started report save request");
        toggleControls(false);

        String fileName = StringHelper.getPicturesFolderPath(project).concat("/").concat(item.getPicture().getFileName());
        if(!savePicture || FileHelper.isValidFile(fileName)) {
            ItemRequestParametersResolver resolver = new ItemRequestParametersResolver(REST_ITEMSAVE_KEY, conf, false);
            resolver.postEntity(item, this);
            schedulePicSave = savePicture;
        } else {
            MessageboxHelper.showMessage(this,
                    getResources().getString(R.string.invalidPictureFilenameMessage),
                    getResources().getString(R.string.okTag),
                    () -> close(false));
        }

    }


    @Override
    public void onChangeHappened(int position, View view) {
        writeData("Started list's item change handler");
        Item item = report.getItems().get(position);
        if(view instanceof ImageView) {
            view.setEnabled(false);
            view.setClickable(false);
            Intent intent = new Intent(ItemReportEditActivity.this, PictureViewerActivity.class);
            intent.putExtra(ITEM_KEY, item);
            intent.putExtra(ITEM_ID_KEY, position);
            intent.putExtra(PICTURE_CAPTURE_MODE, ITEM_PICTURE_MODE);
            intent.putExtra(REPORT_KEY, report);
            intent.putExtra(PROJECT_KEY, project);
            startActivityForResult(intent, PICTURE_VIEWER_SCREEN_ID);
            view.setEnabled(true);
            view.setClickable(true);
        } else {
            save(item, false);
        }
    }

    private void close(boolean error){
        if(error) {
            setResult(RESULT_CANCELED);
        } else {
            setResult(RESULT_OK);
        }
        finish();
        finishAndRemoveTask();
        writeData("Closing item report edit activity");
        stopLockTask();
        super.onDestroy();
    }

    private void projectLoad(){
        writeData("Started routine to load project from the server");
        toggleControls(false);
        ProjectRequestParametersResolver resolver = new ProjectRequestParametersResolver(REST_QRPROJECTLOAD_KEY, conf, true);
        resolver.getEntity(ProjectHelper.getProject(), this);

    }

    @Override
    public void messageReceived(Map<String, String> data) {
        String qrCode = data.get("qrCode");
        if(qrCode != null) {
//            qrCode = data.replace("\\", "");
            if (ProjectHelper.getQrCode().equals(qrCode)) {
                runOnUiThread(this::projectLoad);
            }
        }
    }

    @Override
    public <T extends DomainEntity> void EntityRestCallback(EntityRestResult<T> result) {
        writeData("Started server response handler");
        if(result.getStatus() == HttpStatus.OK || result.getStatus() == HttpStatus.CREATED) {
            switch (result.getRequestCode()) {
                case REST_ITEMSAVE_KEY:
                    writeData("The response was got from an item save request");
                    /*
                    Item item;
                    item = (Item) result.getEntities().get(0);
                    for (int i = 0; i < report.getItems().size(); i++) {
                        if (report.getItems().get(i).getId().equals(item.getId())) {
                            report.getItems().set(i, item);
                            break;
                        }
                    }
                    updatePendingItemsCount();
                    if(schedulePicSave){
                        toggleControls(false);
                        schedulePicSave = false;
                        ProjectHelper.itemPictureUpload(this, item, project);
                    } else {
                        toggleControls(true);
                        Toast.makeText(this, R.string.changesSavedMessage, Toast.LENGTH_SHORT).show();
                    }
                    */
                    break;
                case REST_QRPROJECTLOAD_KEY:
                    writeData("The response was got from a project load request");
                    Project project = (Project) result.getEntities().get(0);
                    report = (ItemReport) project.getDrawingRefs().get(0).getReports().stream().filter(r -> r.getId().equals(report.getId())).findFirst().orElse(null);
                    if (report != null) {
                        itemAdapter.setItemList(report.getItems());
                        itemAdapter.notifyDataSetChanged();
                    }
                    toggleControls(true);
                    break;
                case REST_REPORTUPLOAD_KEY:
                    /*
                    MessageboxHelper.showMessage(this,
                            getResources().getString(R.string.changesSavedMessage),
                            getResources().getString(R.string.okTag),
                            () -> {
                                toggleControls(false);
                                projectLoad();
                            });
                            */
                    break;
            }
        } else {
            ErrorResponseHandler.handle(result, this, () -> close(true));
        }
    }

    @Override
    public void FileRestCallback(FileRestResult result) {
        if(result.getStatus() == HttpStatus.OK || result.getStatus() == HttpStatus.CREATED) {
            if (result.getRequestCode().equals(REST_PICTUREDOWNLOAD_KEY)) {
                inflateActivityLayout();
                toggleControls(true);
            } else if (result.getRequestCode().equals(REST_PICTUREUPLOAD_KEY)) {
                writeData("The response was got from a picture save request");
                updatePendingItemsCount();
                projectLoad();
            }
            Toast.makeText(this, R.string.changesSavedMessage, Toast.LENGTH_SHORT).show();
        } else {
            ErrorResponseHandler.handle(result, this, () -> close(true));
        }
    }

}
