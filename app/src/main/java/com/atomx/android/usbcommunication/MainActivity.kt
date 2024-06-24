package com.atomx.android.usbcommunication

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.atomx.android.usbcommunication.ui.theme.USBCommunicationTheme
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

class MainActivity : ComponentActivity() {
    companion object {
        private const val ACTION_USB_PERMISSION = "com.atomx.android.usbcommunication.USB_PERMISSION"
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

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            USBCommunicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }

        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        registerReceiver(usbReceiver, filter, RECEIVER_NOT_EXPORTED)

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
                    usbManager.requestPermission(accessory, PendingIntent.getBroadcast(this, 0, permissionIntent,
                        PendingIntent.FLAG_IMMUTABLE))
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

    private fun readData() {
        val buffer = ByteArray(1024)
        var bytesRead: Int
        try {
            while (true) {
                bytesRead = inputStream!!.read(buffer)
                if (bytesRead != -1) {
                    val received = String(buffer, 0, bytesRead)
                    Log.d(TAG, "Data received: $received")
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

    @Composable
    fun Greeting(name: String, modifier: Modifier = Modifier) {
        Column(modifier) {
            Text(
                text = "Hello $name!",
                modifier = modifier
            )
            Button(onClick = {
                if (outputStream != null) {
                    // Example: Write to android accessory
                    Thread { writeData("Hello from Android!") }.start()
                }
            }) {
                Text(text = "Write")
            }
        }
    }
}

//@Preview(showBackground = true)
//@Composable
//fun GreetingPreview() {
//    USBCommunicationTheme {
//        Greeting("Android")
//    }
//}