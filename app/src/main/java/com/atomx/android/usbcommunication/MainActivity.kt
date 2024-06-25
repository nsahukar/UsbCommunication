package com.atomx.android.usbcommunication

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : AppCompatActivity() {
    companion object {
        private const val ACTION_USB_PERMISSION =
            "com.atomx.android.usbcommunication.USB_PERMISSION"
        private const val TAG = "MainActivity"
    }

    private lateinit var usbManager: UsbManager
    private var accessory: UsbAccessory? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var inputStream: FileInputStream? = null
    private var outputStream: FileOutputStream? = null

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {

            val action = intent?.action
            if (ACTION_USB_PERMISSION == action) {
                synchronized(this) {
                    accessory = intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY)
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        accessory?.let { openAccessory(it) } ?: Log.d(TAG, "accessory null")
                    } else {
                        Log.d(TAG, "permission denied for accessory!")
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val writeTextView = findViewById<TextView>(R.id.btn_write)
        writeTextView.setOnClickListener {
            Thread { writeData("Hello from Android!") }.start()
        }

        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        ContextCompat.registerReceiver(
            this,
            usbReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        Log.d(TAG, "Intent action: ${intent.action}")

        if (intent.action == UsbManager.ACTION_USB_ACCESSORY_ATTACHED) {
            Log.d(TAG, "Getting accessory from intent")
            accessory = intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY)
            if (accessory != null) {
                if (usbManager.hasPermission(accessory)) {
                    openAccessory(accessory!!)
                } else {
                    Log.d(TAG, "Getting USB Permission")
                    val permissionIntent = Intent(ACTION_USB_PERMISSION)
                    usbManager.requestPermission(
                        accessory, PendingIntent.getBroadcast(
                            this, 0, permissionIntent,
                            PendingIntent.FLAG_IMMUTABLE
                        )
                    )
                }
            } else {
                Log.d(TAG, "Accessory Null")
            }
        } else {
            Log.d(TAG, "Intent action not USB_DEVICE_ATTACHED! -> ${intent.action}")
        }
    }

    private fun openAccessory(accessory: UsbAccessory) {
        fileDescriptor = usbManager.openAccessory(accessory)
        if (fileDescriptor != null) {
            val fd = fileDescriptor?.fileDescriptor
            inputStream = FileInputStream(fd)
            outputStream = FileOutputStream(fd)

            Log.d(TAG, "Accessory opened")
            Toast.makeText(this, "Accessory opened", Toast.LENGTH_SHORT).show()

//            // Example: Write to android accessory
//            Thread { writeData("Hello from Android!") }.start()

            // Example: Read data from accessory
            Thread { readData() }.start()
        } else {
            Log.d(TAG, "File descriptor null")
        }
    }

    private fun writeData(data: String) {
        try {
            outputStream?.write(data.encodeToByteArray()) ?: Log.d(TAG, "output stream null")
            Log.d(TAG, "Data written: $data")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to write data: $data")
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun readData() {
        val buffer = ByteArray(1024)
        var bytesRead: Int
        try {
            while (true) {
                bytesRead = inputStream!!.read(buffer)
                if (bytesRead != -1) {
                    val received = String(buffer, 0, bytesRead)
                    Log.d(TAG, "Data received: $received")
                    GlobalScope.launch(Dispatchers.Main) {
                        Toast.makeText(applicationContext, received, Toast.LENGTH_LONG).show()
                    }
                } else {
                    break
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to read data: $e")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbReceiver)
        if (fileDescriptor != null) {
            try {
                fileDescriptor?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Failed to close file descriptor: $e")
            }
        }
    }
}
