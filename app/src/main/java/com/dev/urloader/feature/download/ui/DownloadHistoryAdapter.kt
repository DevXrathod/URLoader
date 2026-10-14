package com.dev.urloader.feature.download.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dev.urloader.core.common.DownloadRecord
import com.dev.urloader.core.common.FaviconLoader
import com.dev.urloader.databinding.ItemDownloadHistoryBinding

/**
 * High-performance lightweight adapter for rendering downloaded items.
 * Uses DiffUtil for asynchronous list diffing and micro favicon caching.
 */
class DownloadHistoryAdapter(
    private val onItemClick: (DownloadRecord) -> Unit
) : ListAdapter<DownloadRecord, DownloadHistoryAdapter.ViewHolder>(DiffCallback) {

    class ViewHolder(
        private val binding: ItemDownloadHistoryBinding,
        private val onItemClick: (DownloadRecord) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(record: DownloadRecord) {
            binding.tvItemFilename.text = record.filename

            // Load micro favicon from source URL or domain
            FaviconLoader.loadFavicon(
                binding.ivItemSourceIcon,
                record.url,
                record.platform
            )

            binding.root.setOnClickListener {
                onItemClick(record)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDownloadHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    object DiffCallback : DiffUtil.ItemCallback<DownloadRecord>() {
        override fun areItemsTheSame(oldItem: DownloadRecord, newItem: DownloadRecord): Boolean {
            return oldItem.id == newItem.id || oldItem.filename == newItem.filename
        }

        override fun areContentsTheSame(oldItem: DownloadRecord, newItem: DownloadRecord): Boolean {
            return oldItem == newItem
        }
    }
}
