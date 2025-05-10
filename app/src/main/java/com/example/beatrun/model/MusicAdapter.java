package com.example.beatrun.model;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.beatrun.R;
import java.util.List;


public class MusicAdapter extends RecyclerView.Adapter<MusicAdapter.MusicViewHolder> {
    private List<Music> musicList;
    private final OnMusicClickListener playListener, editListener, deleteListener;

    public interface OnMusicClickListener {
        void onClick(Music music);
    }

    public MusicAdapter(List<Music> musicList, OnMusicClickListener playListener,
                        OnMusicClickListener editListener, OnMusicClickListener deleteListener) {
        this.musicList = musicList;
        this.playListener = playListener;
        this.editListener = editListener;
        this.deleteListener = deleteListener;
    }

    public void updateMusicList(List<Music> newList) {
        this.musicList = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MusicViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.music_item, parent, false);
        return new MusicViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MusicViewHolder holder, int position) {
        Music music = musicList.get(position);
        holder.titleText.setText(music.getTitle());

        holder.playBtn.setOnClickListener(v -> playListener.onClick(music));
        holder.editBtn.setOnClickListener(v -> editListener.onClick(music));
        holder.deleteBtn.setOnClickListener(v -> deleteListener.onClick(music));
    }

    @Override
    public int getItemCount() {
        return musicList.size();
    }

    static class MusicViewHolder extends RecyclerView.ViewHolder {
        TextView titleText;
        ImageButton playBtn, editBtn, deleteBtn;

        MusicViewHolder(View itemView) {
            super(itemView);
            titleText = itemView.findViewById(R.id.musicTitle);
            playBtn = itemView.findViewById(R.id.playButton);
            editBtn = itemView.findViewById(R.id.editButton);
            deleteBtn = itemView.findViewById(R.id.deleteButton);
        }
    }
}