package com.github.kr328.clash

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.appcompat.app.AppCompatActivity
import com.github.kr328.clash.core.event.*
import com.github.kr328.clash.core.utils.Log
import com.github.kr328.clash.service.ClashService
import com.github.kr328.clash.service.IClashEventObserver
import com.github.kr328.clash.service.IClashService
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

abstract class BaseActivity : AppCompatActivity(), IClashEventObserver {
    companion object {
        private var clash: IClashService? = null
        private var activityCount: Int = 0

        private var paddingRequest = LinkedBlockingQueue<(IClashService) -> Unit>()
        private var requestHandler: Thread? = null

        private val clashConnection = object: ServiceConnection {
            override fun onServiceDisconnected(name: ComponentName?) {
                synchronized(BaseActivity::class) {
                    Log.i("ClashService disconnected")

                    requestHandler?.interrupt()
                    requestHandler = null
                }
            }

            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                synchronized(BaseActivity::class) {
                    Log.i("ClashService connected")

                    clash = IClashService.Stub.asInterface(service)
                    requestHandler = thread {
                        try {
                            while ( !Thread.currentThread().isInterrupted ) {
                                val block = paddingRequest.take()

                                clash?.run(block)
                            }
                        }
                        catch (e: InterruptedException) {}

                        synchronized(BaseActivity::class) {
                            clash = null
                            requestHandler = null
                        }
                    }
                }
            }
        }
    }

    protected fun runClash(block: (IClashService) -> Unit) {
        paddingRequest.offer(block)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        synchronized(BaseActivity::class) {
            if ( clash == null )
                bindService(Intent(this, ClashService::class.java),
                    clashConnection,
                    Context.BIND_AUTO_CREATE)
            activityCount++;
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        synchronized(BaseActivity::class) {
            if ( --activityCount <= 0 ) {
                unbindService(clashConnection)
            }
        }
    }

    private val observerBinder = object: IClashEventObserver.Stub() {
        override fun onLogEvent(event: LogEvent?) =
            this@BaseActivity.onLogEvent(event)
        override fun onProcessEvent(event: ProcessEvent?) =
            this@BaseActivity.onProcessEvent(event)
        override fun onProxyChangedEvent(event: ProxyChangedEvent?) =
            this@BaseActivity.onProxyChangedEvent(event)
        override fun onErrorEvent(event: ErrorEvent?) =
            this@BaseActivity.onErrorEvent(event)
        override fun onTrafficEvent(event: TrafficEvent?) =
            this@BaseActivity.onTrafficEvent(event)
        override fun onProfileChanged(event: ProfileChangedEvent?) =
            this@BaseActivity.onProfileChanged(event)
    }

    override fun onLogEvent(event: LogEvent?) {}
    override fun onErrorEvent(event: ErrorEvent?) {}
    override fun onProfileChanged(event: ProfileChangedEvent?) {}
    override fun onProcessEvent(event: ProcessEvent?) {}
    override fun onProxyChangedEvent(event: ProxyChangedEvent?) {}
    override fun onTrafficEvent(event: TrafficEvent?) {}
    override fun asBinder(): IBinder {return observerBinder}
}