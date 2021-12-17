package com.lahsuak.flashlightadvance.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lahsuak.flashlightadvance.BuildConfig
import com.lahsuak.flashlightadvance.R
import com.lahsuak.flashlightadvance.adapter.AppListAdapter
import com.lahsuak.flashlightadvance.model.AppModel
import java.util.*
import kotlin.collections.ArrayList

class AppManagerFragment : Fragment(R.layout.fragment_app_manager), SearchView.OnQueryTextListener  {
    private var installedAppsList= ArrayList<AppModel>()
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AppListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        setHasOptionsMenu(true)
        return super.onCreateView(inflater, container, savedInstanceState)
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = view.findViewById<RecyclerView>(R.id.recyclerView)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        installedAppsList = getInstalledApps()
        adapter = AppListAdapter(requireContext(), installedAppsList)
        recyclerView.adapter = adapter
    }


    @SuppressLint("QueryPermissionsNeeded")
    private fun getInstalledApps(): ArrayList<AppModel> {
        installedAppsList.clear()
        val packs = (activity as AppCompatActivity).packageManager.getInstalledPackages(0)
        for (i in packs.indices) {
            val p = packs[i]
            if (!isSystemPackage(p)) {
                val appName = p.applicationInfo.loadLabel(requireActivity().packageManager).toString()
                val icon = p.applicationInfo.loadIcon(requireActivity().packageManager)
                val packages = p.applicationInfo.packageName
                installedAppsList.add(AppModel(appName, icon, packages))
            }
        }
        installedAppsList.sortBy { it.name }
        return installedAppsList
    }

    private fun isSystemPackage(pkgInfo: PackageInfo): Boolean {
        return pkgInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.manager_menu, menu)
        val searchItem = menu.findItem(R.id.action_search)

        val searchView = searchItem.actionView as SearchView
        searchView.setOnQueryTextListener(this)
        searchView.queryHint = "Search Apps"
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId){
            R.id.share_app->{
                try {
                    val intent = Intent(Intent.ACTION_SEND)
                    intent.type = "text/plain"
                    intent.putExtra(Intent.EXTRA_SUBJECT, "Share this App")
                    val shareMsg =
                        "https://play.google.com/store/apps/details?id=" + BuildConfig.APPLICATION_ID + "\n\n"
                    intent.putExtra(Intent.EXTRA_TEXT, shareMsg)
                    requireActivity().startActivity(Intent.createChooser(intent, "Share by"))
                } catch (e: Exception) {
                    notifyUser(
                        requireContext(),
                        "Some thing went wrong!!"
                    )
                }
            }
            R.id.version-> notifyUser(requireContext(),"Current Version ${BuildConfig.VERSION_NAME}")
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        return false
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        val input = newText!!.lowercase(Locale.getDefault())
        val myFiles = ArrayList<AppModel>()
        for (item in installedAppsList) {
            if (item.name.lowercase(Locale.getDefault()).contains(input)) {
                myFiles.add(item)
            }
        }
        adapter.updateList(myFiles)
        return true
    }
    private fun notifyUser(context: Context, message: String){
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}