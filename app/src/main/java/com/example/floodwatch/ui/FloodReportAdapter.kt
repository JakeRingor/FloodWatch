package com.example.floodwatch.ui

import com.example.floodwatch.data.FloodReport
import com.example.floodwatch.R
import com.example.floodwatch.util.ReportTimestampFormatter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton

class FloodReportAdapter(
    private val onWithdraw: ((FloodReport) -> Unit)? = null
) : ListAdapter<FloodReport, FloodReportAdapter.ViewHolder>(REPORT_DIFF) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textSeverity: TextView = view.findViewById(R.id.textSeverity)
        val textStatus: TextView = view.findViewById(R.id.textStatus)
        val textDate: TextView = view.findViewById(R.id.textDate)
        val textFloodLevel: TextView = view.findViewById(R.id.textFloodLevel)
        val textAddress: TextView = view.findViewById(R.id.textAddress)
        val textDescription: TextView = view.findViewById(R.id.textDescription)
        val imageReport: ImageView = view.findViewById(R.id.imageReport)
        val buttonWithdraw: MaterialButton = view.findViewById(R.id.buttonWithdrawReport)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_flood_report, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val report = getItem(position)

        holder.textSeverity.text = holder.itemView.context.getString(
            R.string.severity_format,
            report.severity ?: "N/A"
        )
        holder.textStatus.text = report.status.uppercase()
        holder.textStatus.setTextColor(when (report.status.uppercase()) {
            "VERIFIED" -> "#15803D".toColorInt()
            "DISMISSED", "INVALID_IMAGE", "INVALID_INFORMATION", "WITHDRAWN" ->
                "#B91C1C".toColorInt()
            else -> "#B45309".toColorInt()
        })
        holder.textDate.text = report.createdAt?.let { formatDate(it) } ?: ""
        
        // [CnS] Requirement: Combined Parameter and Passability without changing UI design
        val levelInfo = report.floodLevel ?: "N/A"
        val passInfo = report.passability ?: "Unknown"
        holder.textFloodLevel.text = holder.itemView.context.getString(
            R.string.flood_level_passability_format,
            levelInfo,
            passInfo
        )
        
        holder.textAddress.text = report.address ?: "No address"
        holder.textDescription.text = report.description ?: "No description"

        if (!report.imageUrl.isNullOrEmpty()) {
            holder.imageReport.visibility = View.VISIBLE
            Glide.with(holder.itemView.context)
                .load(report.imageUrl)
                .override(1024, 768)
                .centerCrop()
                .into(holder.imageReport)
        } else {
            Glide.with(holder.itemView.context).clear(holder.imageReport)
            holder.imageReport.visibility = View.GONE
        }

        val canWithdraw = report.id != null &&
            report.status.equals("PENDING", ignoreCase = true) &&
            onWithdraw != null
        holder.buttonWithdraw.visibility = if (canWithdraw) View.VISIBLE else View.GONE
        holder.buttonWithdraw.setOnClickListener {
            if (canWithdraw) onWithdraw.invoke(report)
        }
    }

    private fun formatDate(dateStr: String): String {
        return ReportTimestampFormatter.format(dateStr)
    }

    companion object {
        private val REPORT_DIFF = object : DiffUtil.ItemCallback<FloodReport>() {
            override fun areItemsTheSame(oldItem: FloodReport, newItem: FloodReport): Boolean =
                oldItem.id != null && oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: FloodReport, newItem: FloodReport): Boolean =
                oldItem == newItem
        }
    }
}
