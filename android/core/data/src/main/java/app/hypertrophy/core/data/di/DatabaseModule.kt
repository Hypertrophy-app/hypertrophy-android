package app.hypertrophy.core.data.di

import android.content.Context
import androidx.room.Room
import app.hypertrophy.core.data.local.HypertrophyDatabase
import app.hypertrophy.core.data.local.SquadDao
import app.hypertrophy.core.data.local.TrainingDao
import app.hypertrophy.core.data.local.UserDao
import app.hypertrophy.core.data.local.WorkoutDao
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
    fun provideDatabase(@ApplicationContext context: Context): HypertrophyDatabase =
        Room.databaseBuilder(context, HypertrophyDatabase::class.java, "hypertrophy.db").build()

    @Provides
    fun provideWorkoutDao(database: HypertrophyDatabase): WorkoutDao = database.workoutDao()

    @Provides
    fun provideTrainingDao(database: HypertrophyDatabase): TrainingDao = database.trainingDao()

    @Provides
    fun provideUserDao(database: HypertrophyDatabase): UserDao = database.userDao()

    @Provides
    fun provideSquadDao(database: HypertrophyDatabase): SquadDao = database.squadDao()
}
