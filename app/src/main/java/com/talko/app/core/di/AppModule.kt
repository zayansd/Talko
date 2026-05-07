package com.talko.app.core.di

import android.content.Context
import androidx.room.Room
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.talko.app.data.local.TalkoDatabase
import com.talko.app.data.local.dao.TalkoDao
import com.talko.app.data.repository.AuthRepositoryImpl
import com.talko.app.data.repository.AgoraCallRepositoryImpl
import com.talko.app.data.repository.ChatRepositoryImpl
import com.talko.app.domain.repository.AuthRepository
import com.talko.app.domain.repository.CallRepository
import com.talko.app.domain.repository.ChatRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TalkoDatabase {
        return Room.databaseBuilder(context, TalkoDatabase::class.java, "talko.db")
            .addMigrations(TalkoDatabase.MIGRATION_1_2)
            .build()
    }

    @Provides
    fun provideTalkoDao(database: TalkoDatabase): TalkoDao = database.talkoDao()
}

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {
    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun provideMessaging(): FirebaseMessaging = FirebaseMessaging.getInstance()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository
    @Binds abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository
    @Binds abstract fun bindCallRepository(impl: AgoraCallRepositoryImpl): CallRepository
}
