package io.github.itzmeanjan.intent

import android.app.Activity
import android.content.Intent
import android.content.ComponentName
import android.net.Uri
import android.os.Environment
import android.provider.ContactsContract
import android.provider.MediaStore
import androidx.core.content.FileProvider
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class IntentPlugin(private val registrar: Registrar, private val activity: Activity) : MethodCallHandler {

    private var activityCompletedCallBack: ActivityCompletedCallBack? = null
    lateinit var toBeCapturedImageLocationURI: Uri
    lateinit var tobeCapturedImageLocationFilePath: File

    companion object {
        @JvmStatic
        fun registerWith(registrar: Registrar) {
            val channel = MethodChannel(registrar.messenger(), "intent")
            channel.setMethodCallHandler(IntentPlugin(registrar, registrar.activity()))
        }

    }

    override fun onMethodCall(call: MethodCall, result: Result) {

        // when an activity will be started for getting some result from it, this callback function will handle it
        // then processes received data and send that back to user
        registrar.addActivityResultListener { requestCode, resultCode, intent ->
            when (requestCode) {
                999 -> {
                    if (resultCode == Activity.RESULT_OK) {
                        val filePaths = mutableListOf<String>()
                        if (intent.clipData != null) {
                            var i = 0
                            while (i < intent.clipData?.itemCount!!) {
                                if (intent.type == ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE)
                                    filePaths.add(resolveContacts(intent.clipData?.getItemAt(i)?.uri!!))
                                else
                                    filePaths.add(uriToFilePath(intent.clipData?.getItemAt(i)?.uri!!))
                                i++
                            }
                            activityCompletedCallBack?.sendDocument(filePaths)
                        } else
                            if (intent.type == ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE)
                                filePaths.add(uriToFilePath(intent.data!!))
                            else
                                filePaths.add(resolveContacts(intent.data!!))
                        activityCompletedCallBack?.sendDocument(filePaths)
                        true
                    } else {
                        activityCompletedCallBack?.sendDocument(listOf())
                        false
                    }
                }
                998 -> {
                    if (resultCode == Activity.RESULT_OK) {
                        activityCompletedCallBack?.sendDocument(listOf(tobeCapturedImageLocationFilePath.absolutePath))
                        true
                    } else
                        false
                }
                else -> {
                    false
                }
            }
        }

        when (call.method) {
            // when we're not interested in result of activity started, we call this method via platform channel
            "startActivity" -> {
                val intent = Intent()
                var componentName = "";
                var activityName = "";
                intent.action = call.argument<String>("action")
                if (call.argument<String>("data") != null)
                    intent.data = Uri.parse(call.argument<String>("data"))
                call.argument<Map<String, Any>>("extra")?.apply {
                    this.entries.forEach {
                        if(it.key == "componentName")
                            componentName = it.value as String;
                        else if(it.key == "activityName")
                            activityName = it.value as String;
                        else
                            intent.putExtra(it.key, it.value as String)
                        
                    }
                }
                call.argument<List<Int>>("flag")?.forEach {
                    intent.addFlags(it)
                }
                call.argument<List<String>>("category")?.forEach {
                    intent.addCategory(it)
                }
                if (call.argument<String>("type") != null)
                    intent.type = call.argument<String>("type")
                try {
                    intent.setComponent(ComponentName(componentName, activityName))
                    activity.startActivity(intent)
                } catch (e: Exception) {
                    result.error("Error", e.toString(), null)
                }
            }
            // but if we're interested in getting data from activity launched, we need to call this method
            //
            // cause this method will listen for result of activity launched using onActivityResult callback
            // and then send processed URI to destination
            "startActivityForResult" -> {
                activityCompletedCallBack = object : ActivityCompletedCallBack {
                    override fun sendDocument(data: List<String>) {
                        result.success(data)
                    }
                }
                val activityImageCaptureCode = 998
                val activityIdentifierCode = 999
                val intent = Intent("android.intent.action.MAIN")
                call.argument<Map<String, Any>>("extra")?.apply {
                    this.entries.forEach {
                        intent.putExtra(it.key, it.value as String)
                    }
                }
                call.argument<List<Int>>("flag")?.forEach {
                    intent.addFlags(it)
                }
                call.argument<List<String>>("category")?.forEach {
                    intent.addCategory(it)
                }
                if (call.argument<String>("type") != null)
                    intent.type = call.argument<String>("type")
                try {
                    intent.setComponent(ComponentName("com.appdexter.dexplayer", "com.appdexter.dexplayer.activity.SelectPlayerActivity"))
                    activity.startActivityForResult(intent, activityIdentifierCode)
                } catch (e: Exception) {
                    result.error("Error", e.toString(), null)
                }
            }
            else -> result.notImplemented()
        }
    }

    private fun getImageTempFile(): File? {
        return try {
            val timeStamp = SimpleDateFormat("ddMMyyyy_HHmmss").format(Date())
            val storageDir = activity.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            File.createTempFile("IMG_${timeStamp}", ".jpg", storageDir)
        } catch (e: java.lang.Exception) {
            null
        }
    }

    private fun resolveContacts(uri: Uri): String {
        lateinit var contact: String
        activity.applicationContext.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null).apply {
            this?.moveToFirst()
            contact = this?.getString(getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))!!
            close()
        }
        return contact
    }

    private fun uriToFilePath(uri: Uri): String {
        val cursor = activity.applicationContext.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)
        cursor?.moveToFirst()
        val tmp = cursor?.getString(cursor.getColumnIndex(MediaStore.MediaColumns.DATA))
        cursor?.close()
        return tmp!!
    }

}

interface ActivityCompletedCallBack {
    fun sendDocument(data: List<String>)
}
