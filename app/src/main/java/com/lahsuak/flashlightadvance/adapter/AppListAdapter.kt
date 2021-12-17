package com.lahsuak.flashlightadvance.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.lahsuak.flashlightadvance.model.AppModel
import com.lahsuak.flashlightadvance.R

class AppListAdapter(var context: Context,var applist:ArrayList<AppModel>):
    RecyclerView.Adapter<AppListAdapter.AppViewHolder>() {
    class AppViewHolder(view:View):RecyclerView.ViewHolder(view){
        val icon = view.findViewById<ImageView>(R.id.app_icon)
        val appName = view.findViewById<TextView>(R.id.list_app_name)
        val packageName = view.findViewById<TextView>(R.id.app_package)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.app_item,parent,false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.icon.setImageDrawable(applist[position].icon)
        holder.appName.text = applist[position].name
        holder.packageName.text = applist[position].packages
    }

    override fun getItemCount(): Int {
        return applist.size
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateList(newList: java.util.ArrayList<AppModel>?) {
        applist = java.util.ArrayList()
        applist.clear()
        applist.addAll(newList!!)
        notifyDataSetChanged()
    }
}