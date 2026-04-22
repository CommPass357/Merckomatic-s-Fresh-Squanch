package com.commsfreshsquanch.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.commsfreshsquanch.app.core.AgeBucket
import com.commsfreshsquanch.app.core.PlaylistProfileTagKind
import com.commsfreshsquanch.app.core.PlaylistProfileTagState
import com.commsfreshsquanch.app.core.RecommendationAction
import com.commsfreshsquanch.app.core.RecommendationTab

@Database(
    entities = [
        UserProfileEntity::class,
        SpotifySessionEntity::class,
        PlaylistPairEntity::class,
        CachedTrackEntity::class,
        PlaylistMembershipEntity::class,
        RecommendationHistoryEntity::class,
        DismissedTrackEntity::class,
        AvoidSignalEntity::class,
        SyncLogEntity::class,
        PlaylistProfileTagEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(CfsConverters::class)
abstract class CfsDatabase : RoomDatabase() {
    abstract fun dao(): CfsDao

    companion object {
        fun create(context: Context): CfsDatabase =
            Room.databaseBuilder(context, CfsDatabase::class.java, "comms-fresh-squanch.db").build()
    }
}

class CfsConverters {
    @TypeConverter fun ageBucket(value: AgeBucket): String = value.name
    @TypeConverter fun ageBucket(value: String): AgeBucket = AgeBucket.valueOf(value)
    @TypeConverter fun tab(value: RecommendationTab): String = value.name
    @TypeConverter fun tab(value: String): RecommendationTab = RecommendationTab.valueOf(value)
    @TypeConverter fun action(value: RecommendationAction): String = value.name
    @TypeConverter fun action(value: String): RecommendationAction = RecommendationAction.valueOf(value)
    @TypeConverter fun tagKind(value: PlaylistProfileTagKind): String = value.name
    @TypeConverter fun tagKind(value: String): PlaylistProfileTagKind = PlaylistProfileTagKind.valueOf(value)
    @TypeConverter fun tagState(value: PlaylistProfileTagState): String = value.name
    @TypeConverter fun tagState(value: String): PlaylistProfileTagState = PlaylistProfileTagState.valueOf(value)
}
