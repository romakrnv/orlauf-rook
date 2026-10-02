package com.orlauf.rook

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

enum class Link { IDLE, SCANNING, CONNECTING, READY, LOST }

/**
 * Клиент FTMS для дорожки FITSHOW (имя вида "FS-xxxxxx").
 * Разрешения BLUETOOTH_SCAN / BLUETOOTH_CONNECT проверяются в Activity до вызова connect().
 */
@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
class RookBle(private val context: Context) {

    private companion object {
        fun u(short: String) = UUID.fromString("0000$short-0000-1000-8000-00805f9b34fb")
        val FTMS = u("1826")
        val TREADMILL_DATA = u("2acd")
        val CONTROL_POINT = u("2ad9")
        val CCCD = u("2902")
    }

    val link: StateFlow<Link> get() = _link
    private val _link = MutableStateFlow(Link.IDLE)

    val data = MutableSharedFlow<TreadmillData>(extraBufferCapacity = 16)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 16)

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private var gatt: BluetoothGatt? = null
    private var control: BluetoothGattCharacteristic? = null

    // Последовательная очередь GATT-операций: Android допускает только одну за раз.
    private val queue = ArrayDeque<() -> Boolean>()
    private var busy = false

    private val scanCb = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.device.name ?: result.scanRecord?.deviceName ?: return
            if (!name.startsWith("FS-", ignoreCase = true)) return
            adapter?.bluetoothLeScanner?.stopScan(this)
            _link.value = Link.CONNECTING
            gatt = result.device.connectGatt(context, false, gattCb, BluetoothDevice.TRANSPORT_LE)
        }

        override fun onScanFailed(errorCode: Int) {
            _link.value = Link.LOST
            messages.tryEmit("Ошибка сканирования: $errorCode")
        }
    }

    fun connect() {
        val scanner = adapter?.bluetoothLeScanner
        if (adapter?.isEnabled != true || scanner == null) {
            messages.tryEmit("Включите Bluetooth")
            return
        }
        close()
        _link.value = Link.SCANNING
        scanner.startScan(scanCb)
    }

    fun close() {
        adapter?.bluetoothLeScanner?.stopScan(scanCb)
        synchronized(queue) { queue.clear(); busy = false }
        gatt?.close()
        gatt = null
        control = null
        _link.value = Link.IDLE
    }

    // ---- Команды управления (FTMS Control Point) ----

    fun requestControl() = writeControl(byteArrayOf(0x00))

    /** Старт/продолжить. Дорожка сама отсчитает 3-2-1 и поедет на 1.0 км/ч. */
    fun start() {
        requestControl()
        writeControl(byteArrayOf(0x07))
    }

    /** Скорость в десятых км/ч. Значение жёстко ограничивается диапазоном дорожки. */
    fun setSpeedTenths(tenths: Int) {
        val t = tenths.coerceIn(Config.MIN_SPEED_TENTHS, Config.MAX_SPEED_TENTHS)
        val centi = t * 10 // единица команды: 0.01 км/ч
        writeControl(byteArrayOf(0x02, (centi and 0xFF).toByte(), ((centi shr 8) and 0xFF).toByte()))
    }

    /** ВНИМАНИЕ: у этой дорожки 08 01 полностью выключает питание. */
    fun powerOff() = writeControl(byteArrayOf(0x08, 0x01))

    private fun writeControl(value: ByteArray) {
        enqueue {
            val g = gatt ?: return@enqueue false
            val c = control ?: return@enqueue false
            c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            c.value = value
            g.writeCharacteristic(c)
        }
    }

    // ---- Очередь ----

    private fun enqueue(op: () -> Boolean) {
        synchronized(queue) {
            queue.addLast(op)
            if (!busy) pump()
        }
    }

    private fun pump() {
        // вызывается под synchronized(queue)
        while (queue.isNotEmpty()) {
            val op = queue.removeFirst()
            if (op()) { busy = true; return }
        }
        busy = false
    }

    private fun opDone() {
        synchronized(queue) { busy = false; pump() }
    }

    // ---- GATT ----

    private val gattCb = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                synchronized(queue) { queue.clear(); busy = false }
                control = null
                g.close()
                if (gatt === g) gatt = null
                _link.value = Link.LOST
                messages.tryEmit("Связь с дорожкой потеряна")
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val svc = g.getService(FTMS)
            val td = svc?.getCharacteristic(TREADMILL_DATA)
            val cp = svc?.getCharacteristic(CONTROL_POINT)
            if (svc == null || td == null || cp == null) {
                messages.tryEmit("У устройства нет FTMS (Treadmill Data / Control Point)")
                _link.value = Link.LOST
                return
            }
            control = cp
            g.setCharacteristicNotification(td, true)
            g.setCharacteristicNotification(cp, true)
            enqueue { writeCccd(g, td, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) }
            enqueue { writeCccd(g, cp, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE) }
            requestControl()
            _link.value = Link.READY
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) = opDone()

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) messages.tryEmit("Ошибка записи команды: $status")
            opDone()
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            val v = c.value ?: return
            when (c.uuid) {
                TREADMILL_DATA -> FtmsParser.parse(v)?.let { data.tryEmit(it) }
                CONTROL_POINT -> onControlResponse(v)
            }
        }
    }

    private fun writeCccd(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray): Boolean {
        val d = c.getDescriptor(CCCD) ?: return false
        d.value = value
        return g.writeDescriptor(d)
    }

    /** Ответ: 80 <опкод> <результат>. Результат 01 = успех. */
    private fun onControlResponse(v: ByteArray) {
        if (v.size < 3 || v[0] != 0x80.toByte()) return
        val result = v[2].toInt() and 0xFF
        if (result != 0x01) {
            val op = v[1].toInt() and 0xFF
            messages.tryEmit("Дорожка отклонила команду %02X (код %02X)".format(op, result))
        }
    }
}
