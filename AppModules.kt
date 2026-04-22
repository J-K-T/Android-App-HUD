package com.ringhud.platform.di

import android.content.Context
import androidx.room.Room
import com.ringhud.ble.BleConnectionManager
import com.ringhud.data.AppDatabase
import com.ringhud.data.GestureDao
import com.ringhud.data.SettingsDao
import com.ringhud.gesture.GestureRepository
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
    fun provideContext(@ApplicationContext ctx: Context): Context = ctx
}

@Module
@InstallIn(SingletonComponent::class)
object BleModule {

    @Provides
    @Singleton
    fun provideBleConnectionManager(
        @ApplicationContext ctx: Context
    ): BleConnectionManager = BleConnectionManager(ctx)

    @Provides
    @Singleton
    fun provideGestureRepository(
        bleManager: BleConnectionManager
    ): GestureRepository = GestureRepository(bleManager)
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "ringhud.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideGestureDao(db: AppDatabase): GestureDao = db.gestureDao()

    @Provides
    fun provideSettingsDao(db: AppDatabase): SettingsDao = db.settingsDao()
}
