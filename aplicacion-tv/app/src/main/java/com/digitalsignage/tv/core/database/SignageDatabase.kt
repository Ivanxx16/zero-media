package com.digitalsignage.tv.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.digitalsignage.tv.core.database.converter.RoomConverters
import com.digitalsignage.tv.core.database.dao.CampaignDao
import com.digitalsignage.tv.core.database.dao.ContentVersionDao
import com.digitalsignage.tv.core.database.dao.DeviceDao
import com.digitalsignage.tv.core.database.dao.MediaAssetDao
import com.digitalsignage.tv.core.database.dao.PlaylistDao
import com.digitalsignage.tv.core.database.dao.TelemetryDao
import com.digitalsignage.tv.core.database.entity.CampaignEntity
import com.digitalsignage.tv.core.database.entity.ContentVersionEntity
import com.digitalsignage.tv.core.database.entity.DeviceEntity
import com.digitalsignage.tv.core.database.entity.MediaAssetEntity
import com.digitalsignage.tv.core.database.entity.PlaybackMetricEntity
import com.digitalsignage.tv.core.database.entity.PlaylistEntity
import com.digitalsignage.tv.core.database.entity.PlaylistItemEntity

@Database(
    entities = [
        DeviceEntity::class,
        CampaignEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        MediaAssetEntity::class,
        PlaybackMetricEntity::class,
        ContentVersionEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(RoomConverters::class)
abstract class SignageDatabase : RoomDatabase() {

    abstract fun deviceDao(): DeviceDao
    abstract fun campaignDao(): CampaignDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun mediaAssetDao(): MediaAssetDao
    abstract fun telemetryDao(): TelemetryDao
    abstract fun contentVersionDao(): ContentVersionDao

    companion object {
        private const val DATABASE_NAME = "0media_signage.db"

        @Volatile
        private var instance: SignageDatabase? = null

        fun getInstance(context: Context): SignageDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SignageDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
