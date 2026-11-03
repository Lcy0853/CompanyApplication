package com.example.testmove2

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

// 사진 주소(URI) 리스트를 받아서 화면에 진열해 주는 지배인(Adapter)
class PhotoAdapter(private val photoList: List<Uri>) : RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder>() {

    // 미니 액자(item_photo.xml) 안에 있는 ImageVew를 붙잡아두는 홀더
    class PhotoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivPhoto: ImageView = view.findViewById(R.id.iv_item_photo)
    }

    // 새로운 미니 액자 도화지 (item_photo.xml)를 찍어내는 함수
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_photo, parent, false)
        return PhotoViewHolder(view)
    }

    // 찍어낸 미니 액자에 실제 사진(URI)을 순서대로 끼워 넣는 함수
    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val photoUri = photoList[position]
        holder.ivPhoto.setImageURI(photoUri) // 고화질의 사진을 작게만 만들어서 렉걸림

        // 구글 글라이드를 사용해서 렉 줄임
        //com.bumptech.glide.Glide.with(holder.itemView.context)
        //    .load(photoUri)
        //    .centerCrop()
        //    .into(holder.ivPhoto)
    }

    // 전체 사진이 총 몇 장인지 알려주는 함수
    override fun getItemCount(): Int {
        return photoList.size
    }
}
