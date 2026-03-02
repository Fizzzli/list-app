package com.fizzzli.listapp.di

import android.content.Context
import androidx.room.Room
import com.fizzzli.listapp.data.local.ListDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideListDatabase(
        @ApplicationContext context: Context
    ): ListDatabase {
        return Room.databaseBuilder(
            context,
            ListDatabase::class.java,
            "listapp_database"
        ).build()
    }

    @Provides
    @Singleton
    fun provideListTemplateDao(database: ListDatabase) = database.listTemplateDao()

    @Provides
    @Singleton
    fun provideUserListDao(database: ListDatabase) = database.userListDao()

    @Provides
    @Singleton
    fun provideListItemDao(database: ListDatabase) = database.listItemDao()
}
