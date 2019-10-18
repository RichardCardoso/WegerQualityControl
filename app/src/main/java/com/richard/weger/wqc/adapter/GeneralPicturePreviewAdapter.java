package com.richard.weger.wqc.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.GlideDrawable;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.signature.StringSignature;
import com.richard.weger.wqc.R;
import com.richard.weger.wqc.helper.FileHelper;
import com.richard.weger.wqc.helper.ImageHelper;
import com.richard.weger.wqc.util.GeneralPictureDTO;

import java.io.File;
import java.util.List;

public class GeneralPicturePreviewAdapter extends RecyclerView.Adapter<GeneralPicturePreviewAdapter.GeneralPicturesViewHolder> {

    private String rootPath;
    private List<GeneralPictureDTO> files;
    private PictureTapHandler pictureTapHandler;
    private boolean canRemove;

    public interface PictureTapHandler {
        void onPictureTap(int position);
        void onRemoveRequest(int position);
    }

    public GeneralPicturePreviewAdapter(String picturesPath, List<GeneralPictureDTO> files, PictureTapHandler handler, boolean canRemove) {
        rootPath = picturesPath;

        this.files = files;
        this.pictureTapHandler = handler;
        this.canRemove = canRemove;
    }

    @NonNull
    @Override
    public GeneralPicturesViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.general_picture_item_row, parent, false);
        return new GeneralPicturesViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GeneralPicturesViewHolder holder, int position) {
        String fileName = files.get(position).getFileName();
        boolean processed = files.get(position).isProcessed();
        boolean error = files.get(position).isError();

        holder.setFileName(fileName);
        holder.ivPicture.setOnClickListener(v -> {
            if(pictureTapHandler != null) {
                pictureTapHandler.onPictureTap(position);
            }
        });

        if(error) {
            holder.ivProcessed.setImageResource(R.drawable.ic_error);
        } else {
            holder.ivProcessed.setImageResource(R.drawable.ic_success);
        }

        if(processed){
            holder.ivProcessed.setVisibility(View.VISIBLE);
        } else {
            holder.ivProcessed.setVisibility(View.INVISIBLE);
        }

        holder.removeButton.setOnClickListener(v -> pictureTapHandler.onRemoveRequest(position));

    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    public class GeneralPicturesViewHolder extends RecyclerView.ViewHolder {
        ImageView ivPicture, ivProcessed;
        TextView tvFileName;
        ProgressBar pbLoading;
        ImageButton removeButton;

        GeneralPicturesViewHolder(@NonNull View itemView) {
            super(itemView);
            ivPicture = itemView.findViewById(R.id.ivPicture);
            ivProcessed = itemView.findViewById(R.id.ivProcessed);
            tvFileName = itemView.findViewById(R.id.tvFileName);
            pbLoading = itemView.findViewById(R.id.pbLoading);
            removeButton = itemView.findViewById(R.id.removeButton);

        }

        public void setFileName(String fileName) {
            String filePath;
            filePath = rootPath.concat(File.separator).concat(fileName);
            pbLoading.bringToFront();
            pbLoading.setVisibility(View.VISIBLE);
            removeButton.setVisibility(View.GONE);
            ivPicture.setImageResource(android.R.color.transparent);
            if(FileHelper.isValidFile(filePath)) {
                File f = new File(filePath);
                Glide.with(itemView.getContext()).load(f)
                        .thumbnail(0.5f)
                        .signature(new StringSignature(String.valueOf(f.lastModified())))
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .transform(new ImageHelper.RotateTransformation(itemView.getContext(), ImageHelper.getImageRotation(filePath)))
                        .listener(new RequestListener<File, GlideDrawable>() {
                            @Override
                            public boolean onException(Exception e, File model, Target<GlideDrawable> target, boolean isFirstResource) {
                                pbLoading.setVisibility(View.INVISIBLE);
                                ivPicture.setImageResource(R.drawable.ic_error);
                                return false;
                            }

                            @Override
                            public boolean onResourceReady(GlideDrawable resource, File model, Target<GlideDrawable> target, boolean isFromMemoryCache, boolean isFirstResource) {
                                pbLoading.setVisibility(View.INVISIBLE);
                                removeButton.bringToFront();
                                if(canRemove) {
                                    removeButton.setVisibility(View.VISIBLE);
                                } else {
                                    removeButton.setVisibility(View.GONE);
                                }
                                return false;
                            }
                        })
                        .into(ivPicture);
            }
            if(fileName.contains(".")) {
                tvFileName.setText(fileName.substring(0, fileName.indexOf(".")));
            } else {
                tvFileName.setText(fileName);
            }
        }

    }
}
