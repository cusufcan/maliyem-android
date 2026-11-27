package com.mercan.maliyem.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.time.LocalDate

@Entity(tableName = "savings_goals")
data class SavingsGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val targetMinor: Long,
    val deadline: LocalDate?,
    val icon: String,
    val createdAt: Long,
    val archived: Boolean = false,
)

@Entity(
    tableName = "goal_entries",
    foreignKeys = [ForeignKey(
        entity = SavingsGoalEntity::class,
        parentColumns = ["id"],
        childColumns = ["goalId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("goalId")],
)
data class GoalEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val amountMinor: Long,
    val date: LocalDate,
    val note: String?,
)

data class GoalBalance(
    @Embedded val goal: SavingsGoalEntity,
    val savedMinor: Long,
)

data class GoalDetail(
    @Embedded val balance: GoalBalance,
    @Relation(entity = GoalEntryEntity::class, parentColumn = "id", entityColumn = "goalId")
    val entries: List<GoalEntryEntity>,
)
