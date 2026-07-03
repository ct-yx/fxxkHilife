package com.freebuds.controller.core.transport

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import com.freebuds.controller.util.LogBuffer
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * Shared reflection bridge for Android hidden RFCOMM socket APIs.
 *
 * The production Huawei driver and the generic transport both need the same
 * create/connect/get stream/close sequence. Caching reflected methods here avoids
 * repeating lookups on every connection attempt and keeps connection tuning in one
 * place.
 */
object RfcommSocketBridge {
    data class ConnectedSocket(
        val socket: Any,
        val inputStream: InputStream,
        val outputStream: OutputStream,
        val closeMethod: Method,
    )

    private data class DeviceMethods(
        val createRfcommSocket: Method,
    )

    private data class SocketMethods(
        val connect: Method,
        val close: Method,
        val getInputStream: Method,
        val getOutputStream: Method,
    )

    private val deviceMethodCache = ConcurrentHashMap<Class<*>, DeviceMethods>()
    private val socketMethodCache = ConcurrentHashMap<Class<*>, SocketMethods>()

    fun connect(device: BluetoothDevice, port: Int, logTag: String): ConnectedSocket {
        if (BluetoothAdapter.getDefaultAdapter()?.isDiscovering == true) {
            LogBuffer.i(logTag, "Bluetooth discovery active; cancelling before RFCOMM connect")
            BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery()
        }

        val startedAt = System.currentTimeMillis()
        val deviceMethods = deviceMethodCache.getOrPut(device.javaClass) {
            DeviceMethods(
                createRfcommSocket = device.javaClass.getMethod(
                    "createRfcommSocket",
                    Int::class.javaPrimitiveType,
                )
            )
        }

        val socket = deviceMethods.createRfcommSocket.invoke(device, port)
        val socketMethods = socketMethodCache.getOrPut(socket.javaClass) {
            SocketMethods(
                connect = socket.javaClass.getMethod("connect"),
                close = socket.javaClass.getMethod("close"),
                getInputStream = socket.javaClass.getMethod("getInputStream"),
                getOutputStream = socket.javaClass.getMethod("getOutputStream"),
            )
        }

        socketMethods.connect.invoke(socket)
        val input = socketMethods.getInputStream.invoke(socket) as InputStream
        val output = socketMethods.getOutputStream.invoke(socket) as OutputStream
        LogBuffer.i(logTag, "RFCOMM socket connected in ${System.currentTimeMillis() - startedAt}ms")
        return ConnectedSocket(
            socket = socket,
            inputStream = input,
            outputStream = output,
            closeMethod = socketMethods.close,
        )
    }
}