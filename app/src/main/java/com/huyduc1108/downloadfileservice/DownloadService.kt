package com.huyduc1108.downloadfileservice

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.app.JobIntentService
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URL

class DownloadService : JobIntentService() {
    lateinit var notificationBuilder : NotificationCompat.Builder
    lateinit var notificationManager : NotificationManager
    private val broadcastReceiver : BroadcastReceiver by lazy {
        object : BroadcastReceiver(){
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.let {
                    val process = intent.getIntExtra("progress", 0)
                    Log.e("onReceive: ", "$process")
                    when(process){
                        in 0..99 -> {
                            notificationBuilder.setProgress(100, process, false);
                            notificationManager.notify(100, notificationBuilder.build());
                        }
                        else ->{
                            Log.e("onReceive: ", "vao day")
                            notificationBuilder.setProgress(0, 0, false);
                            notificationManager.cancel(100);
                        }
                    }
                }
            }
        }
    }

    private fun createNotification(){
        notificationBuilder = NotificationCompat
            .Builder(this, "com.huyduc1108.downloadfileservice")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setLargeIcon(
                BitmapFactory.decodeResource(
                    resources,
                    R.drawable.ic_launcher_foreground
                )
            )
            .setContentTitle("File Download")
            .setContentText("Download in progress")
            .setAutoCancel(true)
            .setPriority(NotificationManager.IMPORTANCE_HIGH)
        notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "com.huyduc1108.downloadfileservice",
                "Background Service",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }
        startForeground(100, notificationBuilder.build())
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
    }

    companion object{
        const val JOB_ID = 1
        fun enqueueWork(context: Context?, intent: Intent?) {
            enqueueWork(context!!, DownloadService::class.java, JOB_ID, intent!!)
        }
    }

    override fun onHandleWork(intent: Intent) {
        LocalBroadcastManager.getInstance(this).registerReceiver(
            broadcastReceiver,
            IntentFilter("result_download")
        )

        intent?.let {
            val urlDownload = intent.getStringExtra("url")
            try {
                val url = URL(urlDownload)
                val connection = url.openConnection()
                connection.setRequestProperty("Accept-Encoding", "identity");
                connection.connect()


                var fileLength = connection.contentLength
                val input = BufferedInputStream(connection.getInputStream())
                var filename =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        File(Environment.getDownloadCacheDirectory(), "/DownloadFile")
                    } else {
                        File(
                            Environment.getExternalStorageDirectory().absolutePath +
                                    "/DownloadFile"
                        )
                    }
                if(!filename.exists()) {
                    filename.mkdir()
                }

                val cursor = contentResolver.query(
                    Uri.parse(url.toURI().toString()),
                    null,
                    null,
                    null,
                    null
                )
                cursor?.let {
                    filename = File(
                        filename,
                        cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                    )
                    fileLength = cursor.getLong(cursor.getColumnIndex(OpenableColumns.SIZE)).toInt()
                } ?: kotlin.run {
                    filename = File(
                        filename,
                        urlDownload?.substring(urlDownload?.lastIndexOf('/') + 1)!!
                    )
                }
                if (filename.exists ()) filename.delete ()
                val output = FileOutputStream(filename)

                val data = ByteArray(1024)
                var total = 0
                var count = input.read(data)
                createNotification()
                while (count != -1) {
                    total += count
                    Log.e("onHandleWork: ", "${(total * 100 / fileLength)}")
                    val intent = Intent("result_download")
                    intent.putExtra("progress", (total * 100 / fileLength))
                    LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
                    output.write(data, 0, count)
                    count = input.read(data)
                }
                output.flush()
                output.close()
                input.close()
                val intent = Intent("result_download")
                intent.putExtra("progress", 100)
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            }catch (e: Exception){
                e.printStackTrace()
            }
        }
    }
}