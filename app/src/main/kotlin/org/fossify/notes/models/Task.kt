package org.fossify.notes.models

import kotlinx.serialization.Serializable
import org.fossify.commons.helpers.SORT_BY_TITLE
import org.fossify.commons.helpers.SORT_DESCENDING
import org.fossify.notes.helpers.CollatorBasedComparator

sealed class NoteItem

@Serializable
data class Task(
    val id: Int,
    val dateCreated: Long = 0L,
    val title: String,
    val isDone: Boolean
) : NoteItem(), Comparable<Task> {

    companion object {
        var sorting = 0
    }

    override fun compareTo(other: Task): Int {
        var result = when {
            sorting and SORT_BY_TITLE != 0 -> CollatorBasedComparator().compare(title, other.title)
            else -> dateCreated.compareTo(other.dateCreated)
        }

        if (sorting and SORT_DESCENDING != 0) {
            result *= -1
        }

        return result
    }
}

data class CompletedTasks(
    val tasks: List<Task>,
    val expanded: Boolean
) : NoteItem() {
    val id = -42
}

