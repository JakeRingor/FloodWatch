package com.example.floodwatch

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.*

class FloodReportAdapter(
    private val reports: List<FloodReport>,
    private val onWithdraw: ((FloodReport) -> Unit)? = null
) :
    RecyclerView.Adapter<FloodReportAdapter.ViewHolder>() {

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
        val report = reports[position]

        holder.textSeverity.text = "Severity ${report.severity ?: "N/A"}"
        holder.textStatus.text = report.status.uppercase()
        holder.textStatus.setTextColor(when (report.status.uppercase()) {
            "VERIFIED" -> android.graphics.Color.parseColor("#15803D")
            "DISMISSED", "INVALID_IMAGE", "INVALID_INFORMATION", "WITHDRAWN" ->
                android.graphics.Color.parseColor("#B91C1C")
            else -> android.graphics.Color.parseColor("#B45309")
        })
        holder.textDate.text = report.createdAt?.let { formatDate(it) } ?: ""
        
        // [CnS] Requirement: Combined Parameter and Passability without changing UI design
        val levelInfo = report.floodLevel ?: "N/A"
        val passInfo = report.passability ?: "Unknown"
        holder.textFloodLevel.text = "Level: $levelInfo | Passable: $passInfo"
        
        holder.textAddress.text = report.address ?: "No address"
        holder.textDescription.text = report.description ?: "No description"

        if (!report.imageUrl.isNullOrEmpty()) {
            holder.imageReport.visibility = View.VISIBLE
            Glide.with(holder.itemView.context)
                .load(report.imageUrl)
                .centerCrop()
                .into(holder.imageReport)
        } else {
            holder.imageReport.visibility = View.GONE
        }

        val canWithdraw = report.id != null &&
            report.status.equals("PENDING", ignoreCase = true) &&
            onWithdraw != null
        holder.buttonWithdraw.visibility = if (canWithdraw) View.VISIBLE else View.GONE
        holder.buttonWithdraw.setOnClickListener {
            if (canWithdraw) onWithdraw?.invoke(report)
        }
    }

    override fun getItemCount() = reports.size

    private fun formatDate(dateStr: String): String {
        return try {
            val input = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX", Locale.getDefault())
            val output = SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.getDefault())
            output.format(input.parse(dateStr)!!)
        } catch (e: Exception) {
            dateStr
        }
    }
}
