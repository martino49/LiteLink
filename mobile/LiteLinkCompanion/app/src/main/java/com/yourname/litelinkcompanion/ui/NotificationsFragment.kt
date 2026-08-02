package com.yourname.litelinkcompanion.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.yourname.litelinkcompanion.NotificationStore
import com.yourname.litelinkcompanion.R
import com.yourname.litelinkcompanion.WebSocketManager
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Notifications feed (Screen 6): captured phone notifications with search and filters. */
class NotificationsFragment : Fragment() {

    private var container: LinearLayout? = null
    private var emptyView: TextView? = null
    private var countView: TextView? = null
    private var filterRow: LinearLayout? = null
    private var query = ""
    private var selectedApp = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_notifications, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        container = view.findViewById(R.id.notifContainer)
        emptyView = view.findViewById(R.id.notifEmpty)
        countView = view.findViewById(R.id.notifCount)
        filterRow = view.findViewById(R.id.filterChips)

        view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.notifSearchInput)
            .addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    query = s?.toString() ?: ""
        render()
        WebSocketManager.onPcNotification = { data: JSONObject ->
            requireActivity().runOnUiThread {
                NotificationStore.add(
                    requireContext().applicationContext,
                    data.optString("app", "PC"),
                    data.optString("title", "PC"),
                    data.optString("text", "")
                )
                render()
            }
        }
    }
            })

        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val app = requireContext().applicationContext
        val items = NotificationStore.list(app, query, selectedApp)
        countView?.text = items.size.toString()
        container?.removeAllViews()
        emptyView?.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE

        val fmt = SimpleDateFormat("HH:mm", Locale.US)
        items.forEach { item ->
            val card = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_notification, container, false)
            card.findViewById<TextView>(R.id.notifAppBadge).text = item.optString("app", "APP")
            card.findViewById<TextView>(R.id.notifTitle).text = item.optString("title", "")
            val body = item.optString("text", "")
            card.findViewById<TextView>(R.id.notifBody).apply {
                text = body
                visibility = if (body.isBlank()) View.GONE else View.VISIBLE
            }
            val time = item.optLong("time", System.currentTimeMillis())
            card.findViewById<TextView>(R.id.notifTime).text = fmt.format(Date(time))
            container?.addView(card)
        }
        renderFilters()
    }

    private fun renderFilters() {
        val app = requireContext().applicationContext
        val apps = NotificationStore.apps(app)
        filterRow?.removeAllViews()

        fun chip(label: String, active: Boolean) {
            val tv = TextView(requireContext())
            tv.text = label
            tv.textSize = 12f
            tv.isClickable = true
            tv.isFocusable = true
            val pad = (12 * resources.displayMetrics.density).toInt()
            tv.setPadding(pad, (6 * resources.displayMetrics.density).toInt(), pad, (6 * resources.displayMetrics.density).toInt())
            tv.setBackgroundResource(R.drawable.bg_pill)
            tv.setTextColor(requireContext().getColor(if (active) R.color.ink else R.color.muted))
            tv.setOnClickListener {
                selectedApp = if (active) "" else label
                render()
            }
            filterRow?.addView(tv, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = (8 * resources.displayMetrics.density).toInt() })
        }

        if (selectedApp.isNotEmpty() || apps.isNotEmpty()) {
            chip("All", selectedApp.isEmpty())
        }
        apps.forEach { chip(it, it == selectedApp) }
    }
}
